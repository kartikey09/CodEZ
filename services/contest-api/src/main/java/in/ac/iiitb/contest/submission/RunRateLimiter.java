package in.ac.iiitb.contest.submission;

import in.ac.iiitb.contest.error.RunRateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * "Run" (practice against sample tests) gets its own independent rate limit, separate from
 * Submit's cooldown: max 3 Run requests per rolling 12-second fixed window per user. This is a
 * lazy fixed window — INCR creates the key with a 12s TTL on the first hit in a window; once
 * that key expires, the next Run call starts a fresh window. No sliding-window bookkeeping needed.
 */
@Component
public class RunRateLimiter {

    private static final long MAX_RUNS_PER_WINDOW = 3;
    private static final Duration WINDOW = Duration.ofSeconds(12);

    private final StringRedisTemplate redis;

    public RunRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(long userId) {
        String key = "run:rl:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        if (count != null && count > MAX_RUNS_PER_WINDOW) {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            throw new RunRateLimitExceededException(ttl != null && ttl > 0 ? ttl : WINDOW.getSeconds());
        }
    }
}
