package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ac.iiitb.orchestrator.judge0.Judge0Exception;

/**
 * The ACK invariant that the P0-1 change must preserve. Judging used to be sequential on the read thread;
 * it now runs on judge threads. Because {@link SubmissionProcessor#process} owns the ACK and performs it
 * only after a terminal outcome (and NOT on a Judge0 outage), moving judging off the read thread keeps
 * at-least-once delivery intact. These tests lock that behaviour so a future refactor of the processor
 * can't silently turn a transient Judge0 outage into a lost submission.
 *
 * The processor is unchanged by P0-1; this suite guards the guarantee the concurrency design leans on.
 */
class SubmissionProcessorAckSemanticsTest {

    private static final String STREAM = "subq";
    private static final String GROUP = "workers";

    private static WorkerProperties props() {
        return new WorkerProperties(
            STREAM, GROUP, "orchestrator-1",
            5000L, 10,
            150L, 1500L, 30000L, 8192,
            "inflight:", "ch:user:",
            1, 30000L, 90000L, 50, 3,
            5, 15000L, 1000L,
            16, 60000L,
            500, 3_600_000L);
    }

    private static MapRecord<String, Object, Object> record(long submissionId) {
        Map<Object, Object> body = new HashMap<>();
        body.put("submissionId", Long.toString(submissionId));
        return StreamRecords.mapBacked(body).withStreamKey(STREAM).withId(RecordId.of("0-" + submissionId));
    }

    @SuppressWarnings("unchecked")
    private static StreamOperations<String, Object, Object> stubStreamOps(StringRedisTemplate redis) {
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        return streamOps;
    }

    @Test
    void successAcksPublishesReleasesTheLockAndRecordsSuccess() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOps = stubStreamOps(redis);
        SubmissionStore store = mock(SubmissionStore.class);
        TestCache tests = mock(TestCache.class);
        JudgeService judge = mock(JudgeService.class);
        Judge0CircuitBreaker breaker = new Judge0CircuitBreaker(5, 15000L, () -> 0L);

        when(store.loadJob(7L)).thenReturn(new JobRow(7, 42, 5, 3, "cpp", "code", "queued", "submit"));
        when(store.loadProblem(5L)).thenReturn(new ProblemRow(5, 1000, 256, 1));
        when(tests.get(5L, 1)).thenReturn(List.of(new TestRow(1, "in", "out", false)));
        when(judge.judge(any(), any(), any(), anyBoolean()))
            .thenReturn(new JudgeOutcome(Verdict.AC, null, 1, 1, 10, 100, null, List.of()));

        SubmissionProcessor processor = new SubmissionProcessor(
            redis, store, tests, judge, breaker, props(), new ObjectMapper());

        MapRecord<String, Object, Object> rec = record(7);
        processor.process(rec);

        verify(store).writeVerdict(eq(7L), any(JudgeOutcome.class));
        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), any(RecordId.class)); // acked after the verdict
        verify(redis).convertAndSend(eq("ch:user:42"), anyString());               // result pushed to the user
        verify(redis).delete("inflight:42");                                        // one-in-flight lock released
        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.failureCount()).isZero();
    }

    @Test
    void aJudge0OutageLeavesTheRecordPendingAndDoesNotReleaseTheLock() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOps = stubStreamOps(redis);
        SubmissionStore store = mock(SubmissionStore.class);
        TestCache tests = mock(TestCache.class);
        JudgeService judge = mock(JudgeService.class);
        Judge0CircuitBreaker breaker = new Judge0CircuitBreaker(5, 15000L, () -> 0L);

        when(store.loadJob(8L)).thenReturn(new JobRow(8, 42, 5, 3, "cpp", "code", "queued", "submit"));
        when(store.loadProblem(5L)).thenReturn(new ProblemRow(5, 1000, 256, 1));
        when(tests.get(5L, 1)).thenReturn(List.of(new TestRow(1, "in", "out", false)));
        when(judge.judge(any(), any(), any(), anyBoolean()))
            .thenThrow(new Judge0Exception("judge0 unreachable", new RuntimeException("connect timeout")));

        SubmissionProcessor processor = new SubmissionProcessor(
            redis, store, tests, judge, breaker, props(), new ObjectMapper());

        int before = breaker.failureCount();
        processor.process(record(8));

        // THE invariant: an outage must not ack (so the reclaimer revisits it) and must not free the lock.
        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
        verify(store, never()).writeVerdict(anyLong(), any(JudgeOutcome.class));
        verify(redis, never()).delete(anyString());
        assertThat(breaker.failureCount()).as("a transport failure is recorded on the breaker")
            .isEqualTo(before + 1);
    }

    @Test
    void anAlreadyJudgedRedeliveryIsAckedWithoutRejudging() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOps = stubStreamOps(redis);
        SubmissionStore store = mock(SubmissionStore.class);
        JudgeService judge = mock(JudgeService.class);
        Judge0CircuitBreaker breaker = new Judge0CircuitBreaker(5, 15000L, () -> 0L);

        when(store.loadJob(9L)).thenReturn(new JobRow(9, 42, 5, 3, "cpp", "code", "done", "submit"));

        SubmissionProcessor processor = new SubmissionProcessor(
            redis, store, mock(TestCache.class), judge, breaker, props(), new ObjectMapper());

        processor.process(record(9));

        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), any(RecordId.class)); // idempotent: ack and move on
        verify(judge, never()).judge(any(), any(), any(), anyBoolean());           // never re-judged
        verify(store, never()).writeVerdict(anyLong(), any(JudgeOutcome.class));
        verify(redis, never()).delete(anyString());
    }

    @Test
    void aVanishedSubmissionIsAckedAndSkipped() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOps = stubStreamOps(redis);
        SubmissionStore store = mock(SubmissionStore.class);
        JudgeService judge = mock(JudgeService.class);
        Judge0CircuitBreaker breaker = new Judge0CircuitBreaker(5, 15000L, () -> 0L);

        when(store.loadJob(404L)).thenReturn(null);   // row gone

        SubmissionProcessor processor = new SubmissionProcessor(
            redis, store, mock(TestCache.class), judge, breaker, props(), new ObjectMapper());

        processor.process(record(404));

        verify(streamOps).acknowledge(eq(STREAM), eq(GROUP), any(RecordId.class));
        verify(judge, never()).judge(any(), any(), any(), anyBoolean());
    }
}
