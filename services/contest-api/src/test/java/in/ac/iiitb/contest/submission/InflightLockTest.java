package in.ac.iiitb.contest.submission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * P0-4 against a real Redis. Proves the two properties the fix depends on:
 *
 *   1. acquisition is mutually exclusive (SET NX) — a second attempt can't take a held lock;
 *   2. release is owner-scoped (compare-and-delete) — a stale attempt's release does NOT clear a newer
 *      attempt's lock. That second property is the whole cascade fix: without it, a slow submission whose
 *      lock expired would blindly DEL the lock a *later* submission now holds, letting a third job in flight.
 *
 * Uses a throwaway redis:7 container (same image as the app's integration tests) with distinct keys per
 * test, so no shared state and no flush between tests.
 */
class InflightLockTest {

    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    static StringRedisTemplate redis;
    static InflightLock lock;

    @BeforeAll
    static void init() {
        LettuceConnectionFactory cf = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();
        lock = new InflightLock(redis);
    }

    @Test
    void acquireIsMutuallyExclusive() {
        String key = "inflight:acquire";

        assertThat(lock.tryAcquire(key, "attemptA", 120)).isTrue();
        assertThat(lock.tryAcquire(key, "attemptB", 120)).isFalse();   // held — second attempt rejected
        assertThat(redis.opsForValue().get(key)).isEqualTo("attemptA");
    }

    @Test
    void releaseByNonOwnerDoesNotFreeAnotherAttemptsLock() {
        String key = "inflight:cascade";

        // attempt A takes the lock, then (simulating A's TTL expiring and a *new* submission B taking a fresh
        // lock) the value is now B's token.
        assertThat(lock.tryAcquire(key, "attemptA", 120)).isTrue();
        redis.opsForValue().set(key, "attemptB");

        // A finishes late and tries to release. Compare-and-delete must refuse: it isn't the owner anymore.
        assertThat(lock.releaseIfOwner(key, "attemptA")).isFalse();
        assertThat(redis.opsForValue().get(key)).isEqualTo("attemptB");   // B's lock survived — no cascade

        // B releasing its own lock works and clears the key.
        assertThat(lock.releaseIfOwner(key, "attemptB")).isTrue();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void ownerCanReleaseItsOwnLock() {
        String key = "inflight:owner";

        assertThat(lock.tryAcquire(key, "attemptA", 120)).isTrue();
        assertThat(lock.releaseIfOwner(key, "attemptA")).isTrue();
        assertThat(redis.hasKey(key)).isFalse();
    }
}
