package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * P0-4, worker side, against a real Redis. Opt-in (@Tag("redis"), excluded by default) since it needs Docker:
 *   ./mvnw test -Dgroups=redis
 *
 * Proves the two owner-scoped operations the worker relies on:
 *   refreshIfOwner — re-takes the TTL for the owner, so a long queue wait doesn't count against the judging
 *                    window; a no-op for anyone who isn't the current owner.
 *   releaseIfOwner — compare-and-delete, so a stale attempt can't free a newer one's lock.
 */
@Tag("redis")
class InflightLockRedisIT {

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
    void refreshExtendsTtlForOwnerOnly() {
        String key = "inflight:refresh";
        // owner holds a lock with only 30s left (it sat in the queue a while)
        redis.opsForValue().set(key, "owner", java.time.Duration.ofSeconds(30));
        assertThat(redis.getExpire(key)).isLessThanOrEqualTo(30);

        assertThat(lock.refreshIfOwner(key, "owner", 120)).isTrue();       // markRunning re-takes the TTL
        assertThat(redis.getExpire(key)).isGreaterThan(100);              // now has a full fresh window

        assertThat(lock.refreshIfOwner(key, "someoneElse", 120)).isFalse();  // non-owner can't touch it
    }

    @Test
    void releaseIsOwnerScoped() {
        String key = "inflight:release";
        redis.opsForValue().set(key, "owner");

        assertThat(lock.releaseIfOwner(key, "stale")).isFalse();   // not the owner — no-op
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(lock.releaseIfOwner(key, "owner")).isTrue();    // owner clears it
        assertThat(redis.hasKey(key)).isFalse();
    }
}
