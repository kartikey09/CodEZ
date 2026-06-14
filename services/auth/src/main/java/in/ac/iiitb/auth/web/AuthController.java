package in.ac.iiitb.auth.web;

import in.ac.iiitb.auth.error.AccountDisabledException;
import in.ac.iiitb.auth.error.CurrentPasswordIncorrectException;
import in.ac.iiitb.auth.error.InvalidCredentialsException;
import in.ac.iiitb.auth.error.UnauthorizedException;
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

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final SessionService sessions;
    private final boolean cookieSecure;
    private final long ttlHours;

    public AuthController(UserRepository users,
                          BCryptPasswordEncoder encoder,
                          SessionService sessions,
                          @Value("${app.cookie-secure:false}") boolean cookieSecure,
                          @Value("${app.session.ttl-hours:8}") long ttlHours) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.cookieSecure = cookieSecure;
        this.ttlHours = ttlHours;
    }

    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        // 1) unknown id and 2) wrong password throw the SAME exception -> identical 401.
        User u = users.findByLoginId(req.loginId()).orElseThrow(InvalidCredentialsException::new);
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Only reveal "disabled" to someone who already proved they know the password.
        if (!u.isActive()) {
            throw new AccountDisabledException();
        }
        String sid = sessions.create(toCurrentUser(u));
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
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
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
