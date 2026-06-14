package in.ac.iiitb.auth.session;

/** The identity carried in a session, hydrated from Redis on each request. */
public record CurrentUser(
    long userId,
    String loginId,
    String displayName,
    String role,
    boolean mustChangePassword) {
}
