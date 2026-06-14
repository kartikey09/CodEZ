package in.ac.iiitb.auth.session;

import jakarta.servlet.http.HttpServletRequest;

public class AuthContext {
    public static final String ATTR_USER = "auth.CurrentUser";
    public static final String ATTR_SID = "auth.sid";

    private AuthContext() {
    }

    public static CurrentUser user(HttpServletRequest req){
        return (CurrentUser) req.getAttribute(ATTR_USER);
    }

    public static String sid(HttpServletRequest req) {
        return (String) req.getAttribute(ATTR_SID);
    }
}

