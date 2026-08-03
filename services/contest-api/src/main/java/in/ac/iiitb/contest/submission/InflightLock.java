package in.ac.iiitb.contest.submission;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * contest-api's half of the one-in-flight lock (P0-4). The lock's value is a per-attempt owner token (not a
 * bare timestamp), which makes release <em>safe</em>: a rejected or completed attempt only ever clears the
 * lock if it still owns it.
 *
 *   tryAcquire      — SET key token NX EX ttl. The token is generated per submission attempt and threaded to
 *                     the orchestrator on the subq record so the worker can refresh and release it safely.
 *   releaseIfOwner  — compare-and-delete: only clear the lock if this token still owns it. Without this, a
 *                     rejection path's blind DEL could free a *different* attempt's lock (the P0-4 cascade).
 *
 * releaseIfOwner is single-round-trip Lua so the get-then-delete is atomic on the server — the safe-release
 * discipline Redlock prescribes.
 */
@Component
public class InflightLock {

    /** Delete the key only if its current value is exactly this owner token. Returns 1 if deleted, else 0. */
    private static final RedisScript<Long> RELEASE_IF_OWNER = RedisScript.of(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final StringRedisTemplate redis;

    public InflightLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** SET key token NX EX ttl. True if this attempt now holds the lock. */
    public boolean tryAcquire(String key, String token, long ttlSeconds) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Release {@code key}, but only if {@code token} still owns the lock. A no-op if the lock has expired and
     * been re-taken by a newer submission — which is precisely what prevents the P0-4 cross-deletion cascade.
     */
    public boolean releaseIfOwner(String key, String token) {
        Long deleted = redis.execute(RELEASE_IF_OWNER, List.of(key), token);
        return deleted != null && deleted > 0;
    }
}
