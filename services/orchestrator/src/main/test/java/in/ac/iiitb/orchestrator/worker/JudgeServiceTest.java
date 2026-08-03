package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import in.ac.iiitb.orchestrator.judge0.Judge0Client;
import in.ac.iiitb.orchestrator.judge0.Judge0Result;
import in.ac.iiitb.orchestrator.judge0.Judge0Status;
import in.ac.iiitb.orchestrator.judge0.Judge0Submission;

/**
 * Covers the two judging findings from the engineering review:
 *
 *   P0-2  the batched path actually submits tests in Judge0 batches of {@code batch-size} with one poll loop
 *         per chunk (it was written but dormant behind batch-size: 1).
 *   P0-3  a SUBMIT stops at the first failing test — sequentially, that's one Judge0 call instead of N; in
 *         batches, it stops before the next chunk is submitted. A RUN stays exhaustive so its sample
 *         breakdown is complete, and the whole behaviour is toggleable via submit-early-exit.
 *
 * Judge0Client is mocked, so the number of submit/submitBatch calls is the direct measure of the Judge0
 * load each policy generates — which is the entire point of both findings.
 */
class JudgeServiceTest {

    private final Judge0Client judge0 = mock(Judge0Client.class);

    // ---------- P0-2: batching ----------

    @Test
    void batchedPath_submitsTestsInChunksOfBatchSize() {
        stubBatched(verdicts(Verdict.AC, Verdict.AC, Verdict.AC, Verdict.AC, Verdict.AC, Verdict.AC, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(3, true));   // 7 tests, chunk size 3

        JudgeOutcome out = svc.judge(job("submit"), problem(), tests(7), false);

        verify(judge0, times(3)).submitBatch(any());   // chunks of 3, 3, 1 — one batch call each
        verify(judge0, never()).submit(any());          // never falls back to the one-at-a-time path
        assertThat(out.verdict()).isEqualTo(Verdict.AC);
        assertThat(out.passedTests()).isEqualTo(7);
        assertThat(out.failedTest()).isNull();
    }

    @Test
    void sequentialPath_submitsOnePerTest_whenBatchSizeIsOne() {
        stubSequential(verdicts(Verdict.AC, Verdict.AC, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(1, true));

        JudgeOutcome out = svc.judge(job("submit"), problem(), tests(3), false);

        verify(judge0, times(3)).submit(any());
        verify(judge0, never()).submitBatch(any());
        assertThat(out.verdict()).isEqualTo(Verdict.AC);
        assertThat(out.passedTests()).isEqualTo(3);
    }

    // ---------- P0-3: early exit for SUBMIT ----------

    @Test
    void submit_stopsAtFirstFailure_sequential() {
        // fifth-and-more never run: a wrong SUBMIT costs 3 Judge0 calls, not 5
        stubSequential(verdicts(Verdict.AC, Verdict.AC, Verdict.WA, Verdict.AC, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(1, true));

        JudgeOutcome out = svc.judge(job("submit"), problem(), tests(5), false);

        verify(judge0, times(3)).submit(any());       // stopped right after test 3 failed
        assertThat(out.verdict()).isEqualTo(Verdict.WA);
        assertThat(out.failedTest()).isEqualTo(3);
        assertThat(out.passedTests()).isEqualTo(2);    // passes *before* the failure — ICPC convention
    }

    @Test
    void submit_stopsBeforeNextChunk_batched() {
        // 6 tests, chunk size 2, test 3 fails -> chunks [1,2] and [3,4] run; [5,6] is never submitted
        stubBatched(verdicts(Verdict.AC, Verdict.AC, Verdict.WA, Verdict.AC, Verdict.AC, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(2, true));

        JudgeOutcome out = svc.judge(job("submit"), problem(), tests(6), false);

        verify(judge0, times(2)).submitBatch(any());   // NOT 3 — the amplification is bounded to one chunk
        assertThat(out.verdict()).isEqualTo(Verdict.WA);
        assertThat(out.failedTest()).isEqualTo(3);
        assertThat(out.passedTests()).isEqualTo(2);
    }

    @Test
    void run_isExhaustive_evenWhenEarlyExitEnabled() {
        stubSequential(verdicts(Verdict.AC, Verdict.WA, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(1, true));   // early-exit ON globally

        JudgeOutcome out = svc.judge(job("run"), problem(), samples(3), true);   // ...but this is a RUN

        verify(judge0, times(3)).submit(any());        // every sample ran, despite the WA on test 2
        assertThat(out.tests()).hasSize(3);            // full per-sample breakdown is present
        assertThat(out.verdict()).isEqualTo(Verdict.WA);
        assertThat(out.failedTest()).isEqualTo(2);
        assertThat(out.passedTests()).isEqualTo(2);    // tests 1 and 3
    }

    @Test
    void submit_isExhaustive_whenEarlyExitDisabled() {
        stubSequential(verdicts(Verdict.AC, Verdict.WA, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(1, false));   // toggle OFF

        JudgeOutcome out = svc.judge(job("submit"), problem(), tests(3), false);

        verify(judge0, times(3)).submit(any());        // back to the old exhaustive behaviour
        assertThat(out.verdict()).isEqualTo(Verdict.WA);
        assertThat(out.failedTest()).isEqualTo(2);
    }

    @Test
    void compileError_shortCircuitsImmediately() {
        stubSequential(verdicts(Verdict.CE, Verdict.AC, Verdict.AC));
        JudgeService svc = new JudgeService(judge0, props(1, false));   // even with early-exit off

        JudgeOutcome out = svc.judge(job("submit"), problem(), tests(3), false);

        verify(judge0, times(1)).submit(any());        // CE is input-independent — one call is enough
        assertThat(out.verdict()).isEqualTo(Verdict.CE);
    }

    // ---------- fixtures ----------

    private static List<Verdict> verdicts(Verdict... v) {
        return List.of(v);
    }

    /** Sequential mock: the i-th submit() returns "tok{i}"; get("tok{i}") returns the i-th verdict's result. */
    private void stubSequential(List<Verdict> verdicts) {
        AtomicInteger idx = new AtomicInteger();
        when(judge0.submit(any())).thenAnswer(inv -> "tok" + idx.getAndIncrement());
        for (int i = 0; i < verdicts.size(); i++) {
            when(judge0.get("tok" + i)).thenReturn(result(verdicts.get(i), "tok" + i));
        }
    }

    /** Batched mock: tokens are assigned by global test index, so getBatch maps each token back to its verdict. */
    private void stubBatched(List<Verdict> verdicts) {
        AtomicInteger base = new AtomicInteger();
        when(judge0.submitBatch(any())).thenAnswer(inv -> {
            List<Judge0Submission> subs = inv.getArgument(0);
            int b = base.getAndAdd(subs.size());
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < subs.size(); i++) {
                tokens.add("tok" + (b + i));
            }
            return tokens;
        });
        when(judge0.getBatch(any())).thenAnswer(inv -> {
            List<String> tokens = inv.getArgument(0);
            List<Judge0Result> out = new ArrayList<>();
            for (String t : tokens) {
                int i = Integer.parseInt(t.substring(3));
                out.add(result(verdicts.get(i), t));
            }
            return out;
        });
    }

    private static Judge0Result result(Verdict v, String token) {
        int statusId = switch (v) {
            case AC -> 3;
            case WA -> 4;
            case TLE -> 5;
            case CE -> 6;
            default -> 11;   // RE (memory stays well under the limit, so VerdictMapper keeps it RE)
        };
        String compile = v == Verdict.CE ? "error: expected ';'" : null;
        return new Judge0Result(token, new Judge0Status(statusId, v.name()), null, null, compile, "0.01", 2048);
    }

    private static JobRow job(String kind) {
        return new JobRow(1L, 7L, 5L, 3L, "cpp", "int main(){}", "queued", kind);
    }

    private static ProblemRow problem() {
        return new ProblemRow(5L, 1000, 256, 1);
    }

    private static List<TestRow> tests(int n) {
        List<TestRow> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new TestRow(i, "in" + i, "out" + i, false));
        }
        return list;
    }

    private static List<TestRow> samples(int n) {
        List<TestRow> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new TestRow(i, "in" + i, "out" + i, true));
        }
        return list;
    }

    /** Only batchSize and submitEarlyExit vary across these tests; the rest come from the shared fixture. */
    private static WorkerProperties props(int batchSize, boolean submitEarlyExit) {
        return WorkerPropsFixture.withBatchSize(batchSize, submitEarlyExit);
    }
}
