package in.ac.iiitb.auth.web.admin;

import in.ac.iiitb.auth.event.AuthEventService;
import in.ac.iiitb.auth.event.AuthEventType;
import in.ac.iiitb.auth.session.AuthContext;
import in.ac.iiitb.auth.session.CurrentUser;
import in.ac.iiitb.auth.session.SessionService;
import in.ac.iiitb.auth.user.UserAdminService;
import in.ac.iiitb.auth.web.dto.AdminUserView;
import in.ac.iiitb.auth.web.dto.CreateUserRequest;
import in.ac.iiitb.auth.web.dto.CreatedUser;
import in.ac.iiitb.auth.web.dto.ImportRequest;
import in.ac.iiitb.auth.web.dto.ImportResult;
import in.ac.iiitb.auth.web.dto.ResetResult;
import in.ac.iiitb.auth.web.dto.SetRoleRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin user management (Day 12), hardened on Day 15 with two additions:
 *
 *  - SESSION REVOCATION. A session caches role and active-state at login and is never re-read, so
 *    deactivating or demoting somebody used to leave their live session working with the old
 *    privileges until it expired. Both actions now kill that user's sessions immediately.
 *  - AUDIT. Every administrative action is written to auth_events with the acting admin's id.
 *    Logging happens here rather than in UserAdminService because this is the layer that knows who
 *    is acting; the service stays a pure domain component.
 */
@RestController
@RequestMapping("/auth/admin/users")
public class AdminUserController {

    private final UserAdminService service;
    private final SessionService sessions;
    private final AuthEventService events;

    public AdminUserController(UserAdminService service, SessionService sessions, AuthEventService events) {
        this.service = service;
        this.sessions = sessions;
        this.events = events;
    }

    @GetMapping
    public List<AdminUserView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser create(@Valid @RequestBody CreateUserRequest req, HttpServletRequest http) {
        CreatedUser created = service.create(req);
        events.recordAdminAction(AuthEventType.USER_CREATED, created.loginId(), created.id(),
            actingUserId(http), http, "role=" + created.role());
        return created;
    }

    @PostMapping("/import")
    public ImportResult importRoster(@Valid @RequestBody ImportRequest req, HttpServletRequest http) {
        ImportResult result = service.importCsv(req.csv());
        events.recordAdminAction(AuthEventType.USERS_IMPORTED, null, null, actingUserId(http), http,
            "created=" + result.created().size() + " skipped=" + result.skipped().size());
        return result;
    }

    @PostMapping("/{id}/reset-password")
    public ResetResult resetPassword(@PathVariable long id, HttpServletRequest http) {
        ResetResult result = service.resetPassword(id);
        // The old password is dead, so any session still holding it should be too.
        long killed = sessions.destroyAllForUser(id);
        events.recordAdminAction(AuthEventType.PASSWORD_RESET, result.loginId(), id,
            actingUserId(http), http, "sessionsRevoked=" + killed);
        return result;
    }

    @PostMapping("/{id}/activate")
    public AdminUserView activate(@PathVariable long id, HttpServletRequest http) {
        AdminUserView view = service.setActive(id, true, actingUserId(http));
        events.recordAdminAction(AuthEventType.USER_ACTIVATED, view.loginId(), id, actingUserId(http), http, null);
        return view;
    }

    @PostMapping("/{id}/deactivate")
    public AdminUserView deactivate(@PathVariable long id, HttpServletRequest http) {
        AdminUserView view = service.setActive(id, false, actingUserId(http));
        long killed = sessions.destroyAllForUser(id);   // takes effect now, not at next login
        events.recordAdminAction(AuthEventType.USER_DEACTIVATED, view.loginId(), id,
            actingUserId(http), http, "sessionsRevoked=" + killed);
        return view;
    }

    @PatchMapping("/{id}/role")
    public AdminUserView setRole(@PathVariable long id, @Valid @RequestBody SetRoleRequest req,
                                 HttpServletRequest http) {
        AdminUserView view = service.setRole(id, req.role(), actingUserId(http));
        long killed = sessions.destroyAllForUser(id);   // the cached role in Redis is now wrong
        events.recordAdminAction(AuthEventType.USER_ROLE_CHANGED, view.loginId(), id,
            actingUserId(http), http, "role=" + view.role() + " sessionsRevoked=" + killed);
        return view;
    }

    private static long actingUserId(HttpServletRequest http) {
        CurrentUser u = AuthContext.user(http);
        // The filter guarantees an admin here; -1 only in a controller-slice test that
        // doesn't run the filter, which never exercises the self-protection branch.
        return u != null ? u.userId() : -1L;
    }
}
