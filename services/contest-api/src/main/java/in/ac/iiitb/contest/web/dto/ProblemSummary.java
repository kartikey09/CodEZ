package in.ac.iiitb.contest.web.dto;

/** Listing row — no statement, no tests. */
public record ProblemSummary(long id,
                             String label,
                             String title) {
}
