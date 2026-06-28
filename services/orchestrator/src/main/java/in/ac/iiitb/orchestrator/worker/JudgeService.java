package in.ac.iiitb.orchestrator.worker;

import in.ac.iiitb.orchestrator.judge0.Judge0Client;
import in.ac.iiitb.orchestrator.judge0.Judge0Result;
import in.ac.iiitb.orchestrator.judge0.Judge0Status;
import in.ac.iiitb.orchestrator.judge0.Judge0Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs one submission's tests through Judge0 and produces a verdict.
 *
 * Two strategies, chosen by app.worker.batch-size:
 *   batch-size <= 1  : SEQUENTIAL (Day-7) \u2014 submit a test, poll it, early-exit on the first non-AC test.
 *                      Cheapest on Judge0 CPU (a failing test stops the rest), most HTTP round-trips.
 *   batch-size  > N  : BATCHED \u2014 submit N tests in one /submissions/batch call, poll the batch, evaluate
 *                      in ordinal order. Early-exit is preserved at *chunk* granularity (a failure in an
 *                      early chunk skips later chunks), trading some Judge0 CPU for far fewer round-trips
 *                      and lower latency. Pick a size that balances your Judge0 capacity against latency.
 *
 * In both paths Judge0 compares stdout to expected_output for us, so 3=AC / 4=WA come straight from the
 * status. A CE is input-independent, so the first CE is the whole submission's verdict (compiler output
 * kept, clipped to compile-output-max-bytes). An all-AC run reports the max CPU time and memory seen.
 *
 * COUPLING (Day 6): submit(Judge0Submission)->String, submitBatch(List)->List<String>, get(String)->Result,
 * getBatch(List)->List<Result>, and Judge0Result.status().id()/.time()/.memory()/.compileOutput().
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final Judge0Client judge0;
    private final WorkerProperties props;

    public JudgeService(Judge0Client judge0, WorkerProperties props) {
        this.judge0 = judge0;
        this.props = props;
    }

    public JudgeOutcome judge(JobRow job, ProblemRow problem, List<TestRow> tests) {
        LanguageRuntime rt = LanguageRuntime.fromSlug(job.language());
        double cpu = rt.cpuLimitSeconds(problem.timeLimitMs());
        double wall = rt.wallLimitSeconds(problem.timeLimitMs());
        int memKb = rt.memoryLimitKb(problem.memoryLimitMb());

        return props.batchSize() <= 1
            ? judgeSequential(job.sourceCode(), rt, cpu, wall, memKb, tests)
            : judgeBatched(job.sourceCode(), rt, cpu, wall, memKb, tests);
    }

    // ---- sequential (Day 7) ----

    private JudgeOutcome judgeSequential(String source, LanguageRuntime rt, double cpu, double wall,
                                         int memKb, List<TestRow> tests) {
        int maxTimeMs = 0;
        int maxMemKb = 0;
        for (TestRow t : tests) {
            Judge0Submission sub = new Judge0Submission(source, rt.judge0Id(), t.input(), t.expectedOutput(),
                cpu, wall, memKb);
            Judge0Result res = pollUntilDone(judge0.submit(sub));

            int statusId = statusId(res);
            Integer tMs = parseTimeMs(res.time());
            Integer mKb = res.memory();
            maxTimeMs = max(maxTimeMs, tMs);
            maxMemKb = max(maxMemKb, mKb);

            JudgeOutcome early = verdictFor(statusId, mKb, memKb, t, res, tMs);
            if (early != null) {
                return early;
            }
        }
        return new JudgeOutcome(Verdict.AC, null, maxTimeMs, maxMemKb, null);
    }

    // ---- batched (Day 8) ----

    private JudgeOutcome judgeBatched(String source, LanguageRuntime rt, double cpu, double wall,
                                      int memKb, List<TestRow> tests) {
        int maxTimeMs = 0;
        int maxMemKb = 0;
        int size = Math.max(1, props.batchSize());

        for (int start = 0; start < tests.size(); start += size) {
            List<TestRow> chunk = tests.subList(start, Math.min(start + size, tests.size()));

            List<Judge0Submission> subs = new ArrayList<>(chunk.size());
            for (TestRow t : chunk) {
                subs.add(new Judge0Submission(source, rt.judge0Id(), t.input(), t.expectedOutput(),
                    cpu, wall, memKb));
            }
            List<Judge0Result> results = pollBatchUntilDone(judge0.submitBatch(subs));

            for (int i = 0; i < chunk.size(); i++) {
                TestRow t = chunk.get(i);
                Judge0Result res = i < results.size() ? results.get(i) : null;
                int statusId = statusId(res);
                Integer tMs = res != null ? parseTimeMs(res.time()) : null;
                Integer mKb = res != null ? res.memory() : null;
                maxTimeMs = max(maxTimeMs, tMs);
                maxMemKb = max(maxMemKb, mKb);

                JudgeOutcome early = verdictFor(statusId, mKb, memKb, t, res, tMs);
                if (early != null) {
                    return early;   // chunk-level early-exit: skip the remaining chunks
                }
            }
        }
        return new JudgeOutcome(Verdict.AC, null, maxTimeMs, maxMemKb, null);
    }

    /** Map one test's result; return a terminal outcome on CE or any non-AC, or null to keep going. */
    private JudgeOutcome verdictFor(int statusId, Integer mKb, int memKb, TestRow t, Judge0Result res, Integer tMs) {
        Verdict v = VerdictMapper.map(statusId, mKb, memKb);
        if (v == Verdict.CE) {
            return new JudgeOutcome(Verdict.CE, null, tMs, mKb, clip(res != null ? res.compileOutput() : null));
        }
        if (v != Verdict.AC) {
            return new JudgeOutcome(v, t.ordinal(), tMs, mKb, null);
        }
        return null;
    }

    // ---- Judge0 polling ----

    /** Poll a token with growing backoff until Judge0 leaves status 1/2 (>=3) or we time out -> IE. */
    private Judge0Result pollUntilDone(String token) {
        long waited = 0;
        long backoff = props.pollInitialBackoffMs();    // 0.15 sec
        while (waited < props.pollMaxWaitMs()) {    // 1.5 sec
            Judge0Result res = judge0.get(token);
            Integer id = res.status() != null ? res.status().id() : null;
            if (id != null && id >= 3) {
                return res;
            }
            sleep(backoff);
            waited += backoff;
            backoff = Math.min(backoff * 2, props.pollMaxBackoffMs());
        }
        log.warn("Judge0 token {} did not finish within {} ms", token, props.pollMaxWaitMs());
        return timedOut(token);
    }

    /** Poll a batch of tokens until every result is finished (>=3) or we time out. Order matches tokens. */
    private List<Judge0Result> pollBatchUntilDone(List<String> tokens) {
        long waited = 0;
        long backoff = props.pollInitialBackoffMs();
        List<Judge0Result> last = List.of();// so that when the list is returned at worst last.size() will give 0 and not NPE
        while (waited < props.pollMaxWaitMs()) {
            last = judge0.getBatch(tokens);
            if (allFinished(last, tokens.size())) {
                return last;
            }
            sleep(backoff);
            waited += backoff;
            backoff = Math.min(backoff * 2, props.pollMaxBackoffMs());
        }
        log.warn("Judge0 batch of {} did not finish within {} ms", tokens.size(), props.pollMaxWaitMs());
        return last;   // any unfinished/missing entries fall through to IE via statusId()
    }

    private static boolean allFinished(List<Judge0Result> results, int expected) {
        if (results == null || results.size() < expected) {
            return false;
        }
        for (Judge0Result r : results) {
            Integer id = (r != null && r.status() != null) ? r.status().id() : null;
            if (id == null || id < 3) {
                return false;
            }
        }
        return true;
    }

    // ---- small helpers ----

    private static int statusId(Judge0Result res) {
        return (res != null && res.status() != null && res.status().id() != null) ? res.status().id() : 13;
    }

    private static int max(int cur, Integer candidate) {
        return candidate != null ? Math.max(cur, candidate) : cur;
    }

    private static Judge0Result timedOut(String token) {
        return new Judge0Result(token, new Judge0Status(13, "Timed out"), null, null, null, null, null);
    }

    private String clip(String s) {
        if (s == null) {
            return null;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= props.compileOutputMaxBytes()) {
            return s;
        }
        return new String(bytes, 0, props.compileOutputMaxBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Integer parseTimeMs(String seconds) {
        if (seconds == null || seconds.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(seconds) * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while polling Judge0", e);
        }
    }
}
