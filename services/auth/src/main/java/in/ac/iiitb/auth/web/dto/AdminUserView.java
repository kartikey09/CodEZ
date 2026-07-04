package in.ac.iiitb.auth.web.dto;

import in.ac.iiitb.auth.user.User;

import java.time.Instant;

/** Admin-facing view of a user row (never carries the password hash). */
public record AdminUserView(
        long id,
        String loginId,
        String displayName,
        String role,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt) {

    public static AdminUserView of(User u) {
        return new AdminUserView(u.getId(), u.getLoginId(), u.getDisplayName(),
                u.getRole(), u.isActive(), u.isMustChangePassword(), u.getCreatedAt());
    }
}
