package in.ac.iiitb.contest.web.dto;

import java.time.Instant;

public record SubmissionSummary(
        long id,
        long problemId,
        String language,
        String status,
        String verdict,
        Instant createdAt) {
}
