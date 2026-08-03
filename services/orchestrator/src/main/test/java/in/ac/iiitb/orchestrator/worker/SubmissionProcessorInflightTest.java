package in.ac.iiitb.orchestrator.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * P0-4, worker side. On a verdict the processor must touch the in-flight lock <em>through</em> the owner
 * token, never blindly:
 *
 *   - at markRunning it re-takes the TTL (refreshIfOwner) so queue time doesn't count against the job;
 *   - at the end it releases via compare-and-delete (releaseIfOwner), so a slow job can't free a newer one's
 *     lock;
 *   - and if the record predates the change and carries no token, it falls back to the old blind delete so a
 *     rolling upgrade stays correct.
 */
class SubmissionProcessorInflightTest {

    private StringRedisTemplate redis;
    private SubmissionStore store;
    private TestCache tests;
    private JudgeService judge;
    private InflightLock inflightLock;
    private SubmissionProcessor processor;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        store = mock(SubmissionStore.class);
        tests = mock(TestCache.class);
        judge = mock(JudgeService.class);
        inflightLock = mock(InflightLock.class);
        Judge0CircuitBreaker breaker = mock(Judge0CircuitBreaker.class);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);

        when(store.loadJob(42L)).thenReturn(new JobRow(42L, 7L, 5L, 3L, "cpp", "code", "queued", "submit"));
        when(store.markRunning(42L)).thenReturn(true);
        when(store.loadProblem(5L)).thenReturn(new ProblemRow(5L, 1000, 256, 1));
        when(tests.get(anyLong(), anyInt())).thenReturn(List.of(new TestRow(1, "in", "out", true)));
        when(judge.judge(any(), any(), any(), anyBoolean()))
            .thenReturn(new JudgeOutcome(Verdict.AC, null, 1, 1, 10, 2048, null, List.of()));

        processor = new SubmissionProcessor(redis, store, tests, judge, breaker, inflightLock, props(), new ObjectMapper());
    }

    @Test
    void withToken_refreshesAtMarkRunning_andReleasesByOwner() {
        processor.process(record("42", "tok-abc"));

        verify(inflightLock).refreshIfOwner("inflight:7", "tok-abc", 120L);   // TTL re-taken when judging starts
        verify(inflightLock).releaseIfOwner("inflight:7", "tok-abc");         // compare-and-delete, not blind
        verify(redis, never()).delete(anyString());                          // the blind path is not taken
    }

    @Test
    void withoutToken_fallsBackToBlindDelete() {
        processor.process(record("42", null));   // a record enqueued before P0-4

        verify(inflightLock, never()).refreshIfOwner(anyString(), anyString(), anyLong());
        verify(inflightLock, never()).releaseIfOwner(anyString(), anyString());
        verify(redis).delete("inflight:7");       // old behaviour preserved for the rolling upgrade
    }

    @Test
    void poison_releasesByOwner() {
        // three prior deliveries already happened; this one crosses max-deliveries -> IE + release
        processor.poison(record("42", "tok-abc"));

        verify(store).writeVerdict(eq(42L), any());
        verify(inflightLock).releaseIfOwner("inflight:7", "tok-abc");
    }

    // ---------- fixtures ----------

    private static MapRecord<String, Object, Object> record(String submissionId, String token) {
        Map<Object, Object> fields = new LinkedHashMap<>();
        fields.put("submissionId", submissionId);
        if (token != null) {
            fields.put("inflightToken", token);
        }
        return StreamRecords.<String, Object, Object>mapBacked(fields)
            .withId(RecordId.of("0-1"))
            .withStreamKey("subq");
    }

    private static WorkerProperties props() {
        return WorkerPropsFixture.defaults();   // inflightKeyPrefix "inflight:", inflightTtlSeconds 120
    }
}
