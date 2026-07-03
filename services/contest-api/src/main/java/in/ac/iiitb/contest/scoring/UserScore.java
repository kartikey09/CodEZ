package in.ac.iiitb.contest.scoring;

import java.util.List;

/**
 * What the scorer computes for one user in one contest: how many problems they solved, their total penalty
 * time, the epoch-ms of their most recent accepted solution (handy for display / tie-break), and the
 * per-problem breakdown.
 */
public record UserScore(long userId,
                        int solved,
                        long penalty,
                        long lastAcEpochMs,
                        List<Problem> perProblem
) {

    /**
     * One problem's outcome for this user. {@code penaltyAttempts} is the count of rejected attempts that
     * counted (before the AC if solved; all rejections if not). {@code acMinute} is minutes from contest
     * start to the first AC, or null if unsolved.
     */
    public record Problem(long problemId,
                          boolean solved,
                          int penaltyAttempts,
                          Integer acMinute
    ) {
    }
}
