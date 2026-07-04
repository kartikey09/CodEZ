package in.ac.iiitb.auth.session;

/**
 * Path policy for the session filter. The same rules will be reused by other
 * services (contest-api, realtime) when they adopt session validation.
 */
public final class AuthPaths {

    private AuthPaths() {
    }

    /** No session required at all. */
    public static boolean isPublic(String path) {
        return path.equals("/auth/login")
            || path.equals("/error")
            || path.startsWith("/actuator/");
    }

    /** Reachable even when the user still must change their password. */
    public static boolean allowedDuringMustChange(String path) {
        return path.equals("/auth/me")
            || path.equals("/auth/logout")
            || path.equals("/auth/change-password");
    }

    public static boolean isAdminOnly(String path) {
        return path.equals("/auth/admin") || path.startsWith("/auth/admin/");
    }
}
