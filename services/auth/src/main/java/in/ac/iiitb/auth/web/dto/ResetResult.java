package in.ac.iiitb.auth.web.dto;

/** Result of an admin password reset: the new one-time password, shown ONCE. */
public record ResetResult(
        long id,
        String loginId,
        String initialPassword) {
}
