package in.ac.iiitb.contest.web.dto;

import java.util.List;

/** Full problem view. `samples` only ever contains is_sample tests. */
public record ProblemDetail(
        long id,
        String label,
        String title,
        String statementMd,
        int timeLimitMs,
        int memoryLimitMb,
        List<SampleTest> samples) {
}
