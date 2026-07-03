package in.ac.iiitb.contest.scoring;

import java.util.List;

/**
 * The caller's own standing. {@code rank} is null when the user has no judged submissions in the contest yet.
 */
public record MyStanding(
        long contestId,
        long userId,
        Integer rank,
        int solved,
        long penalty,
        List<StandingsResponse.Cell> cells) {
}
