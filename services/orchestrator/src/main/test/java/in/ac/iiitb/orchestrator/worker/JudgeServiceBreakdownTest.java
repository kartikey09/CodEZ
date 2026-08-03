package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;

import in.ac.iiitb.orchestrator.judge0.Judge0Client;
import in.ac.iiitb.orchestrator.judge0.Judge0Properties;
import in.ac.iiitb.orchestrator.judge0.Judge0Result;
import in.ac.iiitb.orchestrator.judge0.Judge0Status;
import in.ac.iiitb.orchestrator.judge0.Judge0Submission;
import org.junit.jupiter.api.Test;

/**
 * The Run half of {@link JudgeService#judge}: {@code includeBreakdown} is what turns a judged
 * submission into a per-test breakdown, and it must gate ONLY that. A Run and a Submit over the
 * same tests have to agree on verdict/passed/total — otherwise the breakdown flag has quietly
 * become a second judging mode. The Judge0 leg is a stub, so no Judge0 instance is needed
 * (unlike JudgePipelineLiveIT, which covers the same service against the real thing).
 */
class JudgeServiceBreakdownTest {

    private static final ProblemRow SUM = new ProblemRow(1, 1000, 256, 1);
    private static final JobRow RUN = job("run");
    private static final JobRow SUBMIT = job("submit");

    /** Ordinals 1..3; in a real Run these would already be filtered down to samples. */
    private static final List<TestRow> THREE_TESTS = List.of(
            new TestRow(1, "1 1\n", "2\n", true),
            new TestRow(2, "2 2\n", "4\n", true),
            new TestRow(3, "3 3\n", "6\n", true));

    private static final int AC = 3;
    private static final int WA = 4;

    @Test
    void runJob_reportsEveryTestInTheBreakdown() {
        JudgeService judge = judgeServiceReturning(AC, WA, AC);

        JudgeOutcome outcome = judge.judge(RUN, SUM, THREE_TESTS, /* includeBreakdown */ true);

        assertThat(outcome.tests())
                .extracting(TestOutcome::ordinal, TestOutcome::verdict)
                .containsExactly(tuple(1, Verdict.AC), tuple(2, Verdict.WA), tuple(3, Verdict.AC));
    }

    @Test
    void submitJob_leavesTheBreakdownEmpty() {
        JudgeService judge = judgeServiceReturning(AC, WA, AC);

        JudgeOutcome outcome = judge.judge(SUBMIT, SUM, THREE_TESTS, /* includeBreakdown */ false);

        // The per-test list is the one place hidden-test identity could leak out of a Submit.
        assertThat(outcome.tests()).isEmpty();
    }

    @Test
    void breakdownFlagDoesNotChangeTheVerdict() {
        JudgeOutcome run = judgeServiceReturning(AC, WA, AC).judge(RUN, SUM, THREE_TESTS, true);
        JudgeOutcome submit = judgeServiceReturning(AC, WA, AC).judge(SUBMIT, SUM, THREE_TESTS, false);

        assertThat(run.verdict()).isEqualTo(Verdict.WA).isEqualTo(submit.verdict());
        assertThat(run.failedTest()).isEqualTo(2).isEqualTo(submit.failedTest());
        assertThat(run.passedTests()).isEqualTo(2).isEqualTo(submit.passedTests());
        assertThat(run.totalTests()).isEqualTo(3).isEqualTo(submit.totalTests());
    }

    @Test
    void runJob_breakdownSurvivesTheBatchedPath() {
        // batch-size 2 over 3 tests: two chunks, and the breakdown must still come back in ordinal order.
        JudgeService judge = new JudgeService(new StubJudge0(AC, WA, AC), WorkerPropsFixture.withBatchSize(2));

        JudgeOutcome outcome = judge.judge(RUN, SUM, THREE_TESTS, true);

        assertThat(outcome.tests()).extracting(TestOutcome::ordinal).containsExactly(1, 2, 3);
        assertThat(outcome.verdict()).isEqualTo(Verdict.WA);
    }

    private static JudgeService judgeServiceReturning(int... statusIds) {
        return new JudgeService(new StubJudge0(statusIds), WorkerPropsFixture.defaults());
    }

    private static JobRow job(String kind) {
        return new JobRow(1, 7, 1, 1, "python", "print(sum(map(int,input().split())))", "queued", kind);
    }

    /** Hands back one canned Judge0 status per submitted test, in submission order. */
    private static final class StubJudge0 extends Judge0Client {

        private final int[] statusIds;
        private final List<String> submitted = new ArrayList<>();

        StubJudge0(int... statusIds) {
            super(new Judge0Properties("http://stub", "", 1000, 1000, 1));
            this.statusIds = statusIds;
        }

        @Override
        public String submit(Judge0Submission submission) {
            submitted.add(submission.stdin());
            return "token-" + (submitted.size() - 1);
        }

        @Override
        public List<String> submitBatch(List<Judge0Submission> batch) {
            return batch.stream().map(this::submit).toList();
        }

        @Override
        public Judge0Result get(String token) {
            int i = Integer.parseInt(token.substring("token-".length()));
            return new Judge0Result(token, new Judge0Status(statusIds[i], "stub"), null, null, null, "0.010", 1024);
        }

        @Override
        public List<Judge0Result> getBatch(List<String> tokens) {
            return tokens.stream().map(this::get).toList();
        }
    }
}
