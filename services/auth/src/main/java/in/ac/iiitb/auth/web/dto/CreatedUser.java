package in.ac.iiitb.auth.web.dto;

/**
 * Result of creating an account. {@code initialPassword} is the plaintext to hand to
 * the student — shown ONCE and never stored or retrievable again. It is null when the
 * admin supplied their own password (nothing to reveal).
 */
public record CreatedUser(
        long id,
        String loginId,
        String displayName,
        String role,
        String initialPassword) {
}
