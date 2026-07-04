package in.ac.iiitb.auth.web.admin;

import in.ac.iiitb.auth.session.AuthContext;
import in.ac.iiitb.auth.session.CurrentUser;
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
 * Admin user management (Day 12). Every path here is /auth/admin/** and is therefore
 * role-gated by SessionAuthFilter — a non-admin never reaches this controller (403 at
 * the filter). The acting admin's id comes from the session, never the body, so the
 * self-protection checks (can't deactivate or demote yourself) can't be spoofed.
 */
@RestController
@RequestMapping("/auth/admin/users")
public class AdminUserController {

    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req);
    }

    @PostMapping("/import")
    public ImportResult importRoster(@Valid @RequestBody ImportRequest req) {
        return service.importCsv(req.csv());
    }

    @PostMapping("/{id}/reset-password")
    public ResetResult resetPassword(@PathVariable long id) {
        return service.resetPassword(id);
    }

    @PostMapping("/{id}/activate")
    public AdminUserView activate(@PathVariable long id, HttpServletRequest http) {
        return service.setActive(id, true, actingUserId(http));
    }

    @PostMapping("/{id}/deactivate")
    public AdminUserView deactivate(@PathVariable long id, HttpServletRequest http) {
        return service.setActive(id, false, actingUserId(http));
    }

    @PatchMapping("/{id}/role")
    public AdminUserView setRole(@PathVariable long id, @Valid @RequestBody SetRoleRequest req,
                                 HttpServletRequest http) {
        return service.setRole(id, req.role(), actingUserId(http));
    }

    private static long actingUserId(HttpServletRequest http) {
        CurrentUser u = AuthContext.user(http);
        // The filter guarantees an admin here; -1 only in a controller-slice test that
        // doesn't run the filter, which never exercises the self-protection branch.
        return u != null ? u.userId() : -1L;
    }
}
