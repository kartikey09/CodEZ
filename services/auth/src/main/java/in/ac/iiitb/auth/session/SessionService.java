package in.ac.iiitb.auth.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side sessions: an opaque 256-bit token lives in the `sid` cookie;
 * the session record lives in Redis at `sess:{sid}` with a sliding TTL.
 * Postgres remains the source of truth for users; Redis just holds live sessions.
 *
 * Day 15 adds a reverse index, `sessions:user:{userId}` -> set of sids, purely so that live
 * sessions can be REVOKED. Until now a session cached role and active-state at login and was never
 * re-read, so deactivating or demoting somebody left their existing session working with the old
 * privileges until it expired (up to the 8h TTL). That is the gap {@link #destroyAllForUser} closes.
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

    private static String userKey(long userId) {
        return "sessions:user:" + userId;
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

        // reverse index for revocation; TTL is refreshed on every new session for this user
        redis.opsForSet().add(userKey(u.userId()), sid);
        redis.expire(userKey(u.userId()), ttl.plusHours(1));
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
        if (sid == null) {
            return;
        }
        Object userId = redis.opsForHash().get(key(sid), "userId");
        redis.delete(key(sid));
        if (userId != null) {
            redis.opsForSet().remove(userKey(Long.parseLong((String) userId)), sid);
        }
    }

    /**
     * Kill every live session for a user — used when an account is deactivated or its role changes,
     * so the change takes effect immediately instead of at next login. Returns how many were killed.
     */
    public long destroyAllForUser(long userId) {
        Set<String> sids = redis.opsForSet().members(userKey(userId));
        if (sids == null || sids.isEmpty()) {
            redis.delete(userKey(userId));
            return 0;
        }
        long killed = 0;
        for (String sid : sids) {
            if (Boolean.TRUE.equals(redis.delete(key(sid)))) {
                killed++;
            }
        }
        redis.delete(userKey(userId));
        return killed;
    }
}
