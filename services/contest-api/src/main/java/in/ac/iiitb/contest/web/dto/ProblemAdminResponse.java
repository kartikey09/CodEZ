package in.ac.iiitb.contest.web.dto;

public record ProblemAdminResponse(
        long id,
        long contestId,
        String label,
        String title,
        int timeLimitMs,
        int memoryLimitMb) {
}
