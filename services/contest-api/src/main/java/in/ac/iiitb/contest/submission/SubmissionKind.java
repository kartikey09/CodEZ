package in.ac.iiitb.contest.submission;

/** submissions.kind — a real graded attempt vs. a practice run against sample tests only. */
public enum SubmissionKind {
    SUBMIT("submit"),
    RUN("run");

    private final String dbValue;

    SubmissionKind(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
