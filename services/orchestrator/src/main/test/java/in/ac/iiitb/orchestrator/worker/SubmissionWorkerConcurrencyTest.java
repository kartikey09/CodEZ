package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The worker's use of the pool, exercised through the REAL {@link JudgeExecutor} and the REAL
 * {@link SubmissionWorker#dispatchBatch} / {@link SubmissionWorker#desiredReadCount} — only Redis and the
 * processor are stubbed. Redis isn't touched by these paths (dispatch happens after the read), so this runs
 * with no broker: it drives records through the same reserve -> dispatch -> release loop the live loop uses.
 *
 * Guarantees pinned here:
 *   - every read record is judged exactly once (no drops, no double-dispatch);
 *   - judging never exceeds app.worker.judge-concurrency, and is actually driven to it;
 *   - all slots are returned once the batch drains (no permit leak);
 *   - a full batch reserves a full batch, a breaker-open tick reserves a single probe;
 *   - if the pool is shutting down, an undispatched record is left for the reclaimer (process not called).
 */
class SubmissionWorkerConcurrencyTest {

    private static final int CONCURRENCY = 4;

    private static WorkerProperties props(int batchCount) {
        return new WorkerProperties(
            "subq", "workers", "orchestrator-1",
            5000L, batchCount,
            150L, 1500L, 30000L, 8192,
            "inflight:", "ch:user:",
            1,                              // batch-size (Judge0 test batching — unrelated to judge-concurrency)
            30000L, 90000L, 50, 3,
            5, 15000L, 1000L,
            CONCURRENCY, 60000L,            // judge-concurrency, drain-timeout-ms
            500, 3_600_000L,                // test-cache max entries / expire-after-access
            true, 120L);                    // submit-early-exit, inflight-ttl-seconds
    }

    private static MapRecord<String, Object, Object> record(long submissionId) {
        Map<Object, Object> body = new HashMap<>();
        body.put("submissionId", Long.toString(submissionId));
        return StreamRecords.mapBacked(body).withStreamKey("subq").withId(RecordId.of("0-" + submissionId));
    }

    private static Judge0CircuitBreaker closedBreaker() {
        return new Judge0CircuitBreaker(5, 15000L, () -> 0L);   // stays CLOSED, fixed clock
    }

    @Test
    void desiredReadCountIsAFullBatchWhenClosedAndASingleProbeWhenOpen() {
        Judge0CircuitBreaker breaker = closedBreaker();
        JudgeExecutor executor = new JudgeExecutor(CONCURRENCY, 1000);
        SubmissionWorker worker = new SubmissionWorker(
            mock(StringRedisTemplate.class), mock(SubmissionProcessor.class), breaker, executor, props(10));
        try {
            assertThat(worker.desiredReadCount()).isEqualTo(10);   // closed -> full batch
            for (int i = 0; i < 5; i++) {                          // trip the breaker open
                breaker.recordFailure();
            }
            assertThat(breaker.isOpen()).isTrue();
            assertThat(worker.desiredReadCount()).isEqualTo(1);    // open -> single probe
        } finally {
            executor.close();
        }
    }

    @Test
    void everyRecordIsJudgedExactlyOnceAndConcurrencyStaysBounded() throws Exception {
        final int total = CONCURRENCY * 5;
        SubmissionProcessor processor = mock(SubmissionProcessor.class);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicBoolean everExceeded = new AtomicBoolean(false);
        AtomicBoolean duplicate = new AtomicBoolean(false);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(total);
        CyclicBarrier fullHouse = new CyclicBarrier(CONCURRENCY);

        doAnswer(inv -> {
            MapRecord<String, Object, Object> rec = inv.getArgument(0);
            if (!seen.add(rec.getId().getValue())) {
                duplicate.set(true);              // the same record judged twice -> contract violated
            }
            int now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            if (now > CONCURRENCY) {
                everExceeded.set(true);
            }
            try {
                fullHouse.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException ignored) {
                // tail batches, not under assertion
            }
            inFlight.decrementAndGet();
            done.countDown();
            return null;
        }).when(processor).process(any());

        JudgeExecutor executor = new JudgeExecutor(CONCURRENCY, 2000);
        SubmissionWorker worker = new SubmissionWorker(
            mock(StringRedisTemplate.class), processor, closedBreaker(), executor, props(10));

        // Build the records, then drive them through the worker's real reserve/dispatch/release loop.
        Deque<MapRecord<String, Object, Object>> queue = new ArrayDeque<>();
        for (long id = 1; id <= total; id++) {
            queue.add(record(id));
        }
        while (!queue.isEmpty()) {
            int slots = executor.reserve(worker.desiredReadCount());
            List<MapRecord<String, Object, Object>> batch = new ArrayList<>(slots);
            for (int i = 0; i < slots && !queue.isEmpty(); i++) {
                batch.add(queue.poll());
            }
            int dispatched = worker.dispatchBatch(batch, slots);
            assertThat(dispatched).isEqualTo(batch.size());
        }
        executor.close();   // drain

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).as("every record judged exactly once").hasSize(total);
        assertThat(duplicate).as("no record judged twice").isFalse();
        assertThat(everExceeded).as("judging never exceeds judge-concurrency").isFalse();
        assertThat(peak.get()).as("judging is actually driven to full concurrency").isEqualTo(CONCURRENCY);
        assertThat(executor.availablePermits()).as("no permit leaked").isEqualTo(CONCURRENCY);
    }

    @Test
    void aShortReadReturnsTheUnusedSlots() throws Exception {
        SubmissionProcessor processor = mock(SubmissionProcessor.class);
        CountDownLatch ran = new CountDownLatch(2);
        doAnswer(inv -> { ran.countDown(); return null; }).when(processor).process(any());

        JudgeExecutor executor = new JudgeExecutor(CONCURRENCY, 1000);
        SubmissionWorker worker = new SubmissionWorker(
            mock(StringRedisTemplate.class), processor, closedBreaker(), executor, props(10));
        try {
            int slots = executor.reserve(worker.desiredReadCount());   // reserves up to 4
            List<MapRecord<String, Object, Object>> batch = List.of(record(1), record(2)); // but only 2 records read
            int dispatched = worker.dispatchBatch(batch, slots);
            assertThat(dispatched).isEqualTo(2);
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 500 && executor.availablePermits() != CONCURRENCY; i++) { Thread.sleep(2); }
            assertThat(executor.availablePermits())
                .as("slots reserved but not dispatched are returned").isEqualTo(CONCURRENCY);
        } finally {
            executor.close();
        }
    }

    @Test
    void whenThePoolIsShuttingDownUndispatchedRecordsAreLeftForTheReclaimer() throws Exception {
        SubmissionProcessor processor = mock(SubmissionProcessor.class);
        JudgeExecutor executor = new JudgeExecutor(CONCURRENCY, 500);
        SubmissionWorker worker = new SubmissionWorker(
            mock(StringRedisTemplate.class), processor, closedBreaker(), executor, props(10));

        int slots = executor.reserve(worker.desiredReadCount());
        executor.close();   // pool now rejects new work

        int dispatched = worker.dispatchBatch(List.of(record(1), record(2)), slots);

        assertThat(dispatched).as("nothing is dispatched once the pool is closed").isZero();
        verify(processor, never()).process(any());   // records stay pending in the PEL for the reclaimer
        assertThat(executor.availablePermits())
            .as("all reserved slots are returned on shutdown").isEqualTo(CONCURRENCY);
    }
}
