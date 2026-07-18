package in.ac.iiitb.orchestrator.worker;

/** One test's outcome within a judged submission's breakdown (only ever populated for Run jobs). */
public record TestOutcome(int ordinal, Verdict verdict, Integer execTimeMs, Integer memoryKb) {
}
