package in.ac.iiitb.contest.session;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * contest-api only READS sessions — auth-service is the sole issuer. We look up
 * sess:{sid} in the shared Redis and refresh the TTL so activity here keeps the
 * session alive (same 8h sliding window as auth-service).
 */
@Service
public class SessionService {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SessionService(StringRedisTemplate redis,
                          @Value("${app.session.ttl-hours:8}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    private static String key(String sid) {
        return "sess:" + sid;
    }

    public Optional<CurrentUser> resolve(String sid) {
        if (sid == null || sid.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> e = redis.opsForHash().entries(key(sid));
        if (e.isEmpty()) {
            return Optional.empty();
        }
        redis.expire(key(sid), ttl);
        return Optional.of(new CurrentUser(
                Long.parseLong((String) e.get("userId")),
                (String) e.get("loginId"),
                (String) e.get("displayName"),
                (String) e.get("role"),
                Boolean.parseBoolean((String) e.get("mustChange"))));
    }
}
