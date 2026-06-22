package in.ac.iiitb.orchestrator.judge0;

/**
 * One thing to run on Judge0. sourceCode/stdin/expectedOutput are plain text here;
 * the client base64-encodes them on the wire. The *Limit fields are optional (null
 * lets Judge0 use its configured defaults).
 */
public record Judge0Submission(
        String sourceCode,
        int languageId,
        String stdin,
        String expectedOutput,
        Double cpuTimeLimit,
        Double wallTimeLimit,
        Integer memoryLimit) {

    /** Convenience for the common case: no explicit limits. */
    public static Judge0Submission of(String sourceCode, int languageId, String stdin, String expectedOutput) {
        return new Judge0Submission(sourceCode, languageId, stdin, expectedOutput, null, null, null);
    }
}
