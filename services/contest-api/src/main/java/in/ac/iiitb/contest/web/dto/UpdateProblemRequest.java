package in.ac.iiitb.contest.web.dto;

/**
 * Partial edit of a problem. Every field is optional — only the non-null ones are applied, so the
 * authoring UI can save just the statement without resending limits. contestId is deliberately not
 * editable (moving a problem between contests would strand its submissions).
 */
public record UpdateProblemRequest(
        String label,
        String title,
        String statementMd,
        Integer timeLimitMs,
        Integer memoryLimitMb) {
}
