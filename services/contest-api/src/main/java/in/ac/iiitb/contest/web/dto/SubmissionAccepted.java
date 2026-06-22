package in.ac.iiitb.contest.web.dto;

/** 202 body — the work is queued, not judged. */
public record SubmissionAccepted(
        long submissionId,
        String status) {
}
