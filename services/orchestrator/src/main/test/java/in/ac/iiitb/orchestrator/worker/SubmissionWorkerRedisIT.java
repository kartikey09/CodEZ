package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * True end-to-end proof of the P0-1 fix over a REAL Redis Streams broker: the real {@link SubmissionWorker}
 * read loop, the real {@link JudgeExecutor}, real XREADGROUP delivery, and real XACK. Only the judge itself
 * is stubbed (it sleeps to simulate Judge0 I/O and acks through the real template, exactly as the processor
 * would). Proves the three things that matter once judging is concurrent:
 *
 *   1. every queued submission is delivered and judged exactly once;
 *   2. judging genuinely overlaps (peak concurrency {@literal >} 1) and never exceeds the pool size;
 *   3. every record is acked — the PEL drains to empty, so the reclaimer has nothing to recover.
 *
 * Opt-in: tagged {@code redis} and excluded from the default {@code mvn verify} (like the {@code judge0} and
 * {@code chaos} suites), since it needs a Docker daemon. Run it with:  {@code mvn test -Dgroups=redis}
 */
@Tag("redis")
@Testcontainers
class SubmissionWorkerRedisIT {

    private static final String STREAM = "subq";
    private static final String GROUP = "workers";
    private static final int CONCURRENCY = 8;

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration cfg =
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    private static WorkerProperties props() {
        return new WorkerProperties(
            STREAM, GROUP, "orchestrator-it",
            1000L, 20,                       // short BLOCK, batchCount 20
            150L, 1500L, 30000L, 8192,
            "inflight:", "ch:user:",
            1, 30000L, 90000L, 50, 3,
            5, 15000L, 1000L,
            CONCURRENCY, 10000L,
            500, 3_600_000L);
    }

    @Test
    void drainsEveryQueuedSubmissionConcurrentlyAndEmptiesThePel() throws Exception {
        final int total = 40;

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicBoolean everExceeded = new AtomicBoolean(false);
        AtomicBoolean duplicate = new AtomicBoolean(false);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(total);

        SubmissionProcessor processor = mock(SubmissionProcessor.class);
        doAnswer(inv -> {
            MapRecord<String, Object, Object> rec = inv.getArgument(0);
            if (!seen.add(rec.getId().getValue())) {
                duplicate.set(true);
            }
            int now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            if (now > CONCURRENCY) {
                everExceeded.set(true);
            }
            Thread.sleep(25);                                          // simulate Judge0 I/O
            // ack through the real broker, exactly as SubmissionProcessor.process would on success
            redis.opsForStream().acknowledge(STREAM, GROUP, rec.getId());
            inFlight.decrementAndGet();
            done.countDown();
            return null;
        }).when(processor).process(any());

        JudgeExecutor executor = new JudgeExecutor(CONCURRENCY, 10000L);
        Judge0CircuitBreaker breaker = new Judge0CircuitBreaker(5, 15000L, System::currentTimeMillis);
        SubmissionWorker worker = new SubmissionWorker(redis, processor, breaker, executor, props());

        // queue the work, then let the worker drain it
        for (int i = 1; i <= total; i++) {
            Map<Object, Object> body = new HashMap<>();
            body.put("submissionId", Integer.toString(i));
            MapRecord<String, Object, Object> rec =
                StreamRecords.mapBacked(body).withStreamKey(STREAM);
            redis.opsForStream().add(rec);
        }

        worker.start();
        try {
            assertThat(done.await(30, TimeUnit.SECONDS))
                .as("all %d submissions judged within the timeout", total).isTrue();
        } finally {
            worker.stop();
        }

        assertThat(seen).as("every submission judged exactly once").hasSize(total);
        assertThat(duplicate).as("no submission judged twice").isFalse();
        assertThat(everExceeded).as("judging never exceeds the pool size").isFalse();
        assertThat(peak.get()).as("judging genuinely overlapped over real Redis").isGreaterThan(1);
        assertThat(peak.get()).isLessThanOrEqualTo(CONCURRENCY);

        PendingMessagesSummary pending = redis.opsForStream().pending(STREAM, GROUP);
        assertThat(pending.getTotalPendingMessages())
            .as("every record acked — the PEL is empty and the reclaimer has nothing to do").isZero();
    }
}
