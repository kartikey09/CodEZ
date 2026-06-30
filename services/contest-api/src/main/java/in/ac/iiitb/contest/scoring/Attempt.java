package in.ac.iiitb.contest.scoring;

import java.time.Instant;

/**
 * One judged submission reduced to what ICPC scoring needs: which problem, the verdict, the time it was
 * made (submission time, not judging time), and a tie-break (the submission id) so two attempts with an
 * identical timestamp still order deterministically.
 */
public record Attempt(long problemId,
                      String verdict,
                      Instant at,
                      long tieBreak
) {
}
