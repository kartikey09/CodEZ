package in.ac.iiitb.auth.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side sessions: an opaque 256-bit token lives in the `sid` cookie;
 * the session record lives in Redis at `sess:{sid}` with a sliding TTL.
 * Postgres remains the source of truth for users; Redis just holds live sessions.
 */

@Service
public class SessionService {

    private final StringRedisTemplate redis;
    private final SecureRandom rng = new SecureRandom();
    private final Duration ttl;

    public SessionService(StringRedisTemplate redis, @Value("${app.session.ttl-hours}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    private static String key(String sid) {
        return "sess:" + sid;
    }

    public String create(CurrentUser u) {
        byte[] buf = new byte[32];
        rng.nextBytes(buf);
        String sid = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        redis.opsForHash().putAll(key(sid), Map.of(
            "userId", Long.toString(u.userId()),
            "loginId", u.loginId(),
            "displayName", u.displayName(),
            "role", u.role(),
            "mustChange", Boolean.toString(u.mustChangePassword())));
        redis.expire(key(sid), ttl);
        return sid;
    }

    public Optional<CurrentUser> resolve(String sid) {
        if (sid == null || sid.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> e = redis.opsForHash().entries(key(sid));
        if (e.isEmpty()) {
            return Optional.empty();
        }
        redis.expire(key(sid), ttl); // sliding expiry on activity
        return Optional.of(new CurrentUser(
            Long.parseLong((String) e.get("userId")),
            (String) e.get("loginId"),
            (String) e.get("displayName"),
            (String) e.get("role"),
            Boolean.parseBoolean((String) e.get("mustChange"))));
    }

    /** Flip the must-change flag without rotating the session (session survives). */
    public void clearMustChange(String sid) {
        if (sid != null) {
            redis.opsForHash().put(key(sid), "mustChange", "false");
        }
    }

    public void destroy(String sid) {
        if (sid != null) {
            redis.delete(key(sid));
        }
    }
}
