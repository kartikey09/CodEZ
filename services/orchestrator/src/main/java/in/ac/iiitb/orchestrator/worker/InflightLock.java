package in.ac.iiitb.orchestrator.worker;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * The orchestrator's half of the one-in-flight lock (P0-4). contest-api takes the lock (SET NX EX) with a
 * per-attempt owner token as the value and threads that token to the worker on the subq record; the worker
 * uses it here to touch the lock <em>safely</em>:
 *
 *   refreshIfOwner  — re-take the TTL when judging actually starts (markRunning), so a long queue wait
 *                     doesn't count against the lock's window. Fixes half of P0-4 defect #1.
 *   releaseIfOwner  — compare-and-delete: only clear the lock if this token still owns it. Fixes P0-4
 *                     defect #2, the cascade where a slow submission's blind DEL frees a *different*,
 *                     newer submission's lock and lets a third job in flight.
 *
 * Both are single-round-trip Lua so the get-then-act is atomic on the Redis server — the same
 * check-then-delete discipline Redlock prescribes for safe lock release.
 */
@Component
public class InflightLock {

    /** Delete the key only if its current value is exactly this owner token. Returns 1 if deleted, else 0. */
    private static final RedisScript<Long> RELEASE_IF_OWNER = RedisScript.of(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    /** Reset the key's TTL (seconds) only if its current value is exactly this owner token. 1 if refreshed. */
    private static final RedisScript<Long> REFRESH_IF_OWNER = RedisScript.of(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end",
        Long.class);

    private final StringRedisTemplate redis;

    public InflightLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Re-take the TTL on {@code key} to {@code ttlSeconds}, but only if {@code token} still owns the lock.
     * A no-op (returns false) if the lock has since expired or been re-taken by a newer attempt.
     */
    public boolean refreshIfOwner(String key, String token, long ttlSeconds) {
        Long refreshed = redis.execute(REFRESH_IF_OWNER, List.of(key), token, Long.toString(ttlSeconds));
        return refreshed != null && refreshed > 0;
    }

    /**
     * Release {@code key}, but only if {@code token} still owns the lock. Returns false (and touches nothing)
     * if the lock now belongs to a different submission — which is exactly what stops the P0-4 cascade.
     */
    public boolean releaseIfOwner(String key, String token) {
        Long deleted = redis.execute(RELEASE_IF_OWNER, List.of(key), token);
        return deleted != null && deleted > 0;
    }
}
