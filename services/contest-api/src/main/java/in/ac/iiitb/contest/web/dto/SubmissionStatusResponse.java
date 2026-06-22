package in.ac.iiitb.contest.web.dto;

import java.time.Instant;

public record SubmissionStatusResponse(
        long id,
        long problemId,
        String language,
        String status,
        String verdict,
        Integer failedTest,
        Integer execTimeMs,
        Integer memoryKb,
        Instant createdAt,
        Instant judgedAt) {
}
