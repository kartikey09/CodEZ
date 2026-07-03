package in.ac.iiitb.contest.scoring;

/**
 * The knobs that decide a penalty. {@code penaltyPerWrong} is the ICPC minutes added per rejected attempt
 * made before the AC. {@code countCompileErrors} decides whether a CE before the AC is one of those
 * rejected attempts (classic ICPC counts it; some judges don't).
 *
 * IE (internal error) is never a penalty — it's a system failure, not the contestant's mistake.
 */
public record ScoringRules(int penaltyPerWrong, boolean countCompileErrors) {

    /** True if this verdict, occurring before the AC, should add a penalty attempt. */
    public boolean isRejection(String verdict) {
        if (verdict == null) {
            return false;                      // not yet judged — shouldn't appear among 'done' rows
        }
        return switch (verdict) {
            case "AC", "IE" -> false;          // AC isn't a penalty; IE is a system error, never penalize
            case "CE" -> countCompileErrors;
            default -> true;                   // WA, TLE, MLE, RE
        };
    }
}
