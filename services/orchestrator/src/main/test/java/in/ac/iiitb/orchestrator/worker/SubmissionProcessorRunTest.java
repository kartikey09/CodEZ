package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The Run/Submit fork in {@link SubmissionProcessor#process}. A Run job must reach Judge0 with the
 * SAMPLE TESTS ONLY — that filter is what stops a practice run from exercising, timing, or
 * reporting on hidden tests, so it is pinned here rather than left to review. Postgres and Redis
 * are stubbed out; the assertion is on exactly which TestRows the judge was handed.
 */
class SubmissionProcessorRunTest {

    private static final long SUBMISSION_ID = 42;
    private static final ProblemRow PROBLEM = new ProblemRow(1, 1000, 256, 7);

    /** Ordinals 1 and 3 are samples; 2 is hidden and a Run must never see it. */
    private static final List<TestRow> ALL_TESTS = List.of(
            new TestRow(1, "1 1\n", "2\n", true),
            new TestRow(2, "9 9\n", "18\n", false),
            new TestRow(3, "2 2\n", "4\n", true));

    @Test
    void runJob_judgesSampleTestsOnlyAndAsksForTheBreakdown() {
        RecordingJudge judge = process("run");

        assertThat(judge.tests).extracting(TestRow::ordinal).containsExactly(1, 3);
        assertThat(judge.tests).extracting(TestRow::sample).containsOnly(true);
        assertThat(judge.includeBreakdown).isTrue();
    }

    @Test
    void submitJob_judgesEveryTestWithoutABreakdown() {
        RecordingJudge judge = process("submit");

        assertThat(judge.tests).extracting(TestRow::ordinal).containsExactly(1, 2, 3);
        assertThat(judge.includeBreakdown).isFalse();
    }

    /** Runs one record of the given kind through the processor and returns what the judge saw. */
    private static RecordingJudge process(String kind) {
        WorkerProperties props = WorkerPropsFixture.defaults();
        StubStore store = new StubStore(kind);
        RecordingJudge judge = new RecordingJudge(props);

        new SubmissionProcessor(
                mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS),   // ack / publish / release are not under test
                store,
                new TestCache(store, props),
                judge,
                new Judge0CircuitBreaker(props.breakerFailureThreshold(), props.breakerOpenMs(),
                        System::currentTimeMillis),
                props,
                new ObjectMapper())
                .process(record());

        assertThat(judge.tests).as("judge was never called — the processor bailed out early").isNotNull();
        return judge;
    }

    private static MapRecord<String, Object, Object> record() {
        return StreamRecords.<String, Object, Object>mapBacked(
                        Map.<Object, Object>of(SubmissionProcessor.FIELD_SUBMISSION_ID, String.valueOf(SUBMISSION_ID)))
                .withStreamKey("subq")
                .withId(RecordId.of("1-0"));
    }

    /** Captures the judge call instead of talking to Judge0. */
    private static final class RecordingJudge extends JudgeService {

        List<TestRow> tests;
        boolean includeBreakdown;

        RecordingJudge(WorkerProperties props) {
            super(null, props);
        }

        @Override
        public JudgeOutcome judge(JobRow job, ProblemRow problem, List<TestRow> tests, boolean includeBreakdown) {
            this.tests = tests;
            this.includeBreakdown = includeBreakdown;
            return new JudgeOutcome(Verdict.AC, null, tests.size(), tests.size(), 10, 1024, null, List.of());
        }
    }

    /** Postgres stand-in: one queued job of the requested kind, one problem, three tests. */
    private static final class StubStore extends SubmissionStore {

        private final String kind;

        StubStore(String kind) {
            super(new JdbcTemplate());
            this.kind = kind;
        }

        @Override
        public JobRow loadJob(long submissionId) {
            return new JobRow(submissionId, 7, PROBLEM.id(), 1, "python", "print(1)", "queued", kind);
        }

        @Override
        public boolean markRunning(long submissionId) {
            return true;
        }

        @Override
        public ProblemRow loadProblem(long problemId) {
            return PROBLEM;
        }

        @Override
        public List<TestRow> loadTests(long problemId) {
            return ALL_TESTS;
        }

        @Override
        public boolean writeVerdict(long submissionId, JudgeOutcome outcome) {
            return true;
        }
    }
}
