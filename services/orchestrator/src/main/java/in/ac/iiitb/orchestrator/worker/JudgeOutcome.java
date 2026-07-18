package in.ac.iiitb.orchestrator.worker;

import java.util.List;

/**
 * Result of judging a submission. {@code failedTest} is the ordinal of the first failing test
 * (null on AC/CE) — kept for internal bookkeeping, but the API layer no longer surfaces it, since
 * a bare ordinal could hint at hidden-test structure. {@code passedTests}/{@code totalTests} back
 * the "X of Y passed" display for both Submit and Run. {@code tests} is a per-test breakdown that
 * is only ever non-empty when the caller asked for one (Run jobs only, which only ever judge
 * sample tests) — enforced inside {@link JudgeService}, never left to the caller.
 */
public record JudgeOutcome(Verdict verdict,
                           Integer failedTest,
                           int passedTests,
                           int totalTests,
                           Integer execTimeMs,
                           Integer memoryKb,
                           String compileOutput,
                           List<TestOutcome> tests) {
}
