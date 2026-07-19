package in.ac.iiitb.contest.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ac.iiitb.contest.web.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the session cookie minted by auth-service on every contest-api
 * request. Public paths pass; anything else needs a live session (401 otherwise),
 * and a user who still must change their password is blocked entirely (403) — they
 * have to go back to auth-service and change it before they can see problems.
 *
 * Registered via FilterRegistrationBean in SecurityConfig (not @Component) so the
 * @WebMvcTest slice for HealthController doesn't try to build it without Redis.
 */
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

        String raw = req.getRequestURI();
        // Day 15: authorise on the CANONICAL path. getRequestURI() is the raw, undecoded path while
        // Spring routes on the decoded/normalised one, so matching the raw form let /api/%61dmin/...
        // and /api//admin/... reach admin handlers without tripping the admin prefix test.
        if (RequestPaths.isSuspicious(raw)) {
            writeError(res, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", "Malformed request path");
            return;
        }
        String path = RequestPaths.canonical(raw);
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
        if (user.mustChangePassword()) {
            writeError(res, HttpServletResponse.SC_FORBIDDEN,
                    "PASSWORD_CHANGE_REQUIRED", "You must change your password before continuing");
            return;
        }

        if(!"admin".equals(user.role()) && AuthPaths.isAdminOnly(path)) {
            writeError(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Admin role required");
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
