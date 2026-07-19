package in.ac.iiitb.auth.event;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes the audit trail. Every method swallows its own failures: an audit write must never be the
 * reason a student can't log in. A dropped row is logged loudly instead.
 */
@Service
public class AuthEventService {

    private static final Logger log = LoggerFactory.getLogger(AuthEventService.class);

    /** Cap what we store from client-controlled headers. */
    private static final int MAX_UA = 256;
    private static final int MAX_DETAIL = 512;

    private final AuthEventRepository events;

    public AuthEventService(AuthEventRepository events) {
        this.events = events;
    }

    public void record(AuthEventType type, String loginId, Long userId, Long actorId,
                       String ip, String userAgent, String detail) {
        try {
            events.save(new AuthEvent(type, trim(loginId, 128), userId, actorId, trim(ip, 64),
                trim(userAgent, MAX_UA), trim(detail, MAX_DETAIL)));
        } catch (Exception e) {
            log.warn("auth event {} for {} was not recorded", type, loginId, e);
        }
    }

    /** Convenience for the request-scoped cases. */
    public void record(AuthEventType type, String loginId, Long userId, HttpServletRequest req, String detail) {
        record(type, loginId, userId, null, clientIp(req), userAgent(req), detail);
    }

    public void recordAdminAction(AuthEventType type, String targetLoginId, Long targetUserId,
                                  Long actorId, HttpServletRequest req, String detail) {
        record(type, targetLoginId, targetUserId, actorId, clientIp(req), userAgent(req), detail);
    }

    /**
     * Best-effort client address. X-Forwarded-For is only meaningful behind a proxy you control —
     * anyone can send the header — so it's recorded as a hint for forensics, never used for access
     * control. The throttle keys on it too, which is why only the FIRST hop is taken and it's length
     * capped: an attacker padding the header can at worst throttle themselves.
     */
    public static String clientIp(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return trim((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim(), 64);
        }
        return trim(req.getRemoteAddr(), 64);
    }

    public static String userAgent(HttpServletRequest req) {
        return req == null ? null : trim(req.getHeader("User-Agent"), MAX_UA);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
