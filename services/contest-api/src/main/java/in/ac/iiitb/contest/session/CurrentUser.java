package in.ac.iiitb.contest.session;

/**
 * The identity carried in a session. The field set MUST match what auth-service
 * writes into Redis at sess:{sid} — that hash is the cross-service contract.
 */
public record CurrentUser(
        long userId,
        String loginId,
        String displayName,
        String role,
        boolean mustChangePassword) {
}
