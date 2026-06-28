package in.ac.iiitb.orchestrator.worker;

/** The verdicts we persist (submissions.verdict). MLE is derived, not a raw Judge0 status. */
public enum Verdict {
    AC, WA, TLE, MLE, RE, CE, IE
}
