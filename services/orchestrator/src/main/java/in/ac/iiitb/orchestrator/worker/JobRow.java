package in.ac.iiitb.orchestrator.worker;

/** The submission as loaded from Postgres (source + routing + current status + kind). */
public record JobRow(long id,
                     long userId,
                     long problemId,
                     long contestId,
                     String language,
                     String sourceCode,
                     String status,
                     String kind) {
}
