package in.ac.iiitb.orchestrator.worker;

/** Result of judging a submission. failedTest is the ordinal of the first failing test (null on AC/CE). */
public record JudgeOutcome(Verdict verdict,
                           Integer failedTest,
                           Integer execTimeMs,
                           Integer memoryKb,
                           String compileOutput) {
}
