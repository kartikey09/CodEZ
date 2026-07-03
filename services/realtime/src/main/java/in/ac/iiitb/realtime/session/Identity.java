package in.ac.iiitb.realtime.session;

/**
 * The identity carried in a session hash (sess:{sid}). Field set matches what auth-service writes and what
 * contest-api reads — that hash is the cross-service contract.
 */
public record Identity(
        long userId,
        String loginId,
        String displayName,
        String role,
        boolean mustChangePassword) {
}
