package in.ac.iiitb.auth.web;

import in.ac.iiitb.auth.error.AccountDisabledException;
import in.ac.iiitb.auth.error.CurrentPasswordIncorrectException;
import in.ac.iiitb.auth.error.InvalidCredentialsException;
import in.ac.iiitb.auth.error.UnauthorizedException;
import in.ac.iiitb.auth.event.AuthEventService;
import in.ac.iiitb.auth.event.AuthEventType;
import in.ac.iiitb.auth.security.LoginThrottle;
import in.ac.iiitb.auth.session.AuthContext;
import in.ac.iiitb.auth.session.CurrentUser;
import in.ac.iiitb.auth.session.SessionService;
import in.ac.iiitb.auth.user.User;
import in.ac.iiitb.auth.user.UserRepository;
import in.ac.iiitb.auth.web.dto.ChangePasswordRequest;
import in.ac.iiitb.auth.web.dto.LoginRequest;
import in.ac.iiitb.auth.web.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final SessionService sessions;
    private final boolean cookieSecure;
    private final long ttlHours;
    private final LoginThrottle throttle;
    private final AuthEventService events;

    /**
     * A real bcrypt hash of a value nobody can log in with. When the submitted login id doesn't
     * exist we still run one comparison against this, so an unknown account costs the same time as
     * a wrong password. Without it, response latency tells an attacker which login ids are real.
     */
    private static final String DUMMY_HASH =
        "$2a$12$M8s3PbYyU3wFDA0hJH3rBu6jvBDMwvHqUDEXBEVGYnQnJH.OeMHqK";

    public AuthController(UserRepository users,
                          BCryptPasswordEncoder encoder,
                          SessionService sessions,
                          LoginThrottle throttle,
                          AuthEventService events,
                          @Value("${app.cookie-secure:false}") boolean cookieSecure,
                          @Value("${app.session.ttl-hours:8}") long ttlHours) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.throttle = throttle;
        this.events = events;
        this.cookieSecure = cookieSecure;
        this.ttlHours = ttlHours;
    }

    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest http, HttpServletResponse res) {
        String ip = AuthEventService.clientIp(http);

        // Day 15: refuse before touching the password at all while a lockout is in force.
        try {
            throttle.assertNotLocked(req.loginId(), ip);
        } catch (RuntimeException locked) {
            events.record(AuthEventType.LOGIN_BLOCKED, req.loginId(), null, http, "lockout in force");
            throw locked;
        }

        Optional<User> found = users.findByLoginId(req.loginId());
        // 1) unknown id and 2) wrong password take the SAME path -> identical 401, and the dummy
        // comparison keeps them costing the same wall-clock time.
        boolean passwordOk = found
            .map(u -> encoder.matches(req.password(), u.getPasswordHash()))
            .orElseGet(() -> {
                encoder.matches(req.password(), DUMMY_HASH);
                return false;
            });

        if (!passwordOk) {
            boolean nowLocked = throttle.recordFailure(req.loginId(), ip);
            events.record(nowLocked ? AuthEventType.LOGIN_LOCKED : AuthEventType.LOGIN_FAILED,
                req.loginId(), found.map(User::getId).orElse(null), http,
                found.isPresent() ? "bad password" : "unknown login id");
            throw new InvalidCredentialsException();
        }

        User u = found.get();
        // Only reveal "disabled" to someone who already proved they know the password.
        if (!u.isActive()) {
            events.record(AuthEventType.LOGIN_DISABLED, u.getLoginId(), u.getId(), http, null);
            throw new AccountDisabledException();
        }

        throttle.recordSuccess(req.loginId(), ip);
        String sid = sessions.create(toCurrentUser(u));
        events.record(AuthEventType.LOGIN_SUCCESS, u.getLoginId(), u.getId(), http, null);
        res.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(sid).toString());
        return ResponseEntity.ok(toMe(u));
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest req) {
        CurrentUser u = require(req);
        return new MeResponse(u.userId(), u.loginId(), u.displayName(), u.role(), u.mustChangePassword());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest httpReq) {
        CurrentUser cu = require(httpReq);
        User u = users.findById(cu.userId()).orElseThrow(UnauthorizedException::new);
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new CurrentPasswordIncorrectException();
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setMustChangePassword(false);
        users.save(u);
        sessions.clearMustChange(AuthContext.sid(httpReq)); // same session, flag flipped
        events.record(AuthEventType.PASSWORD_CHANGED, u.getLoginId(), u.getId(), httpReq, "self-service");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        CurrentUser cu = AuthContext.user(req);
        if (cu != null) {
            events.record(AuthEventType.LOGOUT, cu.loginId(), cu.userId(), req, null);
        }
        sessions.destroy(AuthContext.sid(req));
        res.addHeader(HttpHeaders.SET_COOKIE, clearCookie().toString());
        return ResponseEntity.noContent().build();
    }

    private CurrentUser require(HttpServletRequest req) {
        CurrentUser u = AuthContext.user(req);
        if (u == null) {
            throw new UnauthorizedException();
        }
        return u;
    }

    private ResponseCookie sessionCookie(String sid) {
        return ResponseCookie.from("sid", sid)
            .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/")
            .maxAge(Duration.ofHours(ttlHours))
            .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from("sid", "")
            .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/")
            .maxAge(0)
            .build();
    }

    private static MeResponse toMe(User u) {
        return new MeResponse(u.getId(), u.getLoginId(), u.getDisplayName(), u.getRole(), u.isMustChangePassword());
    }

    private static CurrentUser toCurrentUser(User u) {
        return new CurrentUser(u.getId(), u.getLoginId(), u.getDisplayName(), u.getRole(), u.isMustChangePassword());
    }
}
