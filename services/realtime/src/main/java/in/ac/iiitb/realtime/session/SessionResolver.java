package in.ac.iiitb.realtime.session;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import in.ac.iiitb.realtime.config.RealtimeProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Validates a WebSocket handshake the same way contest-api validates a request: look up sess:{sid} in the
 * shared Redis and refresh its TTL so a long-lived socket keeps the session alive. auth-service remains the
 * sole issuer; this only reads.
 */
@Component
public class SessionResolver {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SessionResolver(StringRedisTemplate redis, RealtimeProperties props) {
        this.redis = redis;
        this.ttl = Duration.ofHours(props.sessionTtlHours());
    }

    private static String key(String sid) {
        return "sess:" + sid;
    }

    public Optional<Identity> resolve(String sid) {
        if (sid == null || sid.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> e = redis.opsForHash().entries(key(sid));
        if (e.isEmpty()) {
            return Optional.empty();
        }
        redis.expire(key(sid), ttl);
        return Optional.of(new Identity(
                Long.parseLong((String) e.get("userId")),
                (String) e.get("loginId"),
                (String) e.get("displayName"),
                (String) e.get("role"),
                Boolean.parseBoolean((String) e.get("mustChange"))));
    }
}
