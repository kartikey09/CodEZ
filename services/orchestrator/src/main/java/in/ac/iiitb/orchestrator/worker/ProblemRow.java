package in.ac.iiitb.orchestrator.worker;

// A problem’s judging limits plus its test-data version.

public record ProblemRow(long id,
                         int timeLimitMs,
                         int memoryLimitMb,
                         int testDataVersion) {
}
