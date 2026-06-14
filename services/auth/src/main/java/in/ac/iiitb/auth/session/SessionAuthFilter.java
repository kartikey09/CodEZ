package in.ac.iiitb.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ac.iiitb.auth.web.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the session cookie into a {@link CurrentUser} for every request, and
 * centralises authorization:
 *   - public paths pass through,
 *   - everything else requires a valid session (401 otherwise),
 *   - a must-change user is confined to the change-password allowlist (403 otherwise).
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionService sessions;
    private final ObjectMapper json;

    public SessionAuthFilter(SessionService sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {

        String path = req.getRequestURI();
        String sid = readCookie(req, "sid");

        CurrentUser user = sessions.resolve(sid).orElse(null);
        if (user != null) {
            req.setAttribute(AuthContext.ATTR_USER, user);
            req.setAttribute(AuthContext.ATTR_SID, sid);
        }

        if ("OPTIONS".equalsIgnoreCase(req.getMethod()) || AuthPaths.isPublic(path)) {
            chain.doFilter(req, res);
            return;
        }
        if (user == null) {
            writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        if (user.mustChangePassword() && !AuthPaths.allowedDuringMustChange(path)) {
            writeError(res, HttpServletResponse.SC_FORBIDDEN,
                "PASSWORD_CHANGE_REQUIRED", "You must change your password before continuing");
            return;
        }
        chain.doFilter(req, res);
    }

    private static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(json.writeValueAsString(new ErrorResponse(code, message)));
    }
}
