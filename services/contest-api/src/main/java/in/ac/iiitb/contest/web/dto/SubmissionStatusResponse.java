package in.ac.iiitb.contest.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/submissions/{id}. No raw test ordinal is ever returned (hidden tests must never be
 * identifiable, even by position) — {@code passedTests}/{@code totalTests} is the aggregate for
 * both kinds, and {@code tests} is a per-test breakdown that is only ever non-empty for a
 * {@code kind="run"} row (which by construction only ever judges sample tests).
 */
public record SubmissionStatusResponse(
        long id,
        long problemId,
        String language,
        String status,
        String verdict,
        String kind,
        Integer passedTests,
        Integer totalTests,
        Integer execTimeMs,
        Integer memoryKb,
        Instant createdAt,
        Instant judgedAt,
        List<TestResultDto> tests) {
}
