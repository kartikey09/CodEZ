package in.ac.iiitb.contest.session;

/** Path policy for contest-api. Everything that isn't public needs a valid session. */
public final class AuthPaths {

    private AuthPaths() {
    }

    public static boolean isPublic(String path) {
        return path.equals("/api/health")
                || path.equals("/error")
                || path.startsWith("/actuator/");
    }

    /** Routes only an admin role may touch. */
    public static boolean isAdminOnly(String path){
        return path.equals("/api/admin") || path.startsWith("/api/admin/");
    }
}
