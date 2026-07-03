package in.ac.iiitb.realtime.ws;

import java.util.Map;
import java.util.Optional;

import in.ac.iiitb.realtime.session.Identity;
import in.ac.iiitb.realtime.session.SessionResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Gate the WebSocket upgrade. The browser sends the same `sid` cookie auth-service minted (cookies aren't
 * port-scoped, so it reaches :8083), which we resolve against the shared session store. No session -> 401;
 * a user who still must change their password -> 403; missing/!numeric contestId -> 400. On success the
 * Identity and contestId are stashed in the handshake attributes for the handler.
 */
@Component
public class HandshakeAuthInterceptor implements HandshakeInterceptor {

    static final String ATTR_IDENTITY = "identity";
    static final String ATTR_CONTEST = "contestId";

    private final SessionResolver sessions;

    public HandshakeAuthInterceptor(SessionResolver sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        HttpServletRequest http = servlet.getServletRequest();

        Optional<Identity> resolved = sessions.resolve(readCookie(http, "sid"));
        if (resolved.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Identity identity = resolved.get();
        if (identity.mustChangePassword()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        Long contestId = parseLong(http.getParameter("contestId"));
        if (contestId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        attributes.put(ATTR_IDENTITY, identity);
        attributes.put(ATTR_CONTEST, contestId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
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

    private static Long parseLong(String v) {
        try {
            return v == null ? null : Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
