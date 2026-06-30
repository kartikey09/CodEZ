package in.ac.iiitb.contest.scoring;

import java.time.Instant;
import java.util.List;

/**
 * The leaderboard payload: the problem labels (column order), the ranked rows, and when it was generated.
 */
public record StandingsResponse(
        long contestId,
        List<String> problems,
        List<Row> rows,
        Instant generatedAt
) {

    /** One contestant's line. {@code rank} uses competition ranking (ties share a rank, then it skips). */
    public record Row(
            int rank,
            long userId,
            String displayName,
            int solved,
            long penalty,
            List<Cell> cells
    ) {
    }

    /**
     * One problem cell for a contestant. {@code state} is "solved" | "attempted" | "none";
     * {@code acMinute} is the solve time (null unless solved).
     */
    public record Cell(
            String label,
            String state,
            int attempts,
            Integer acMinute
    ) {
    }
}
