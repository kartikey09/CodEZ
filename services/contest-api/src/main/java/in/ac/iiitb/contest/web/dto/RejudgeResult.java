package in.ac.iiitb.contest.web.dto;

/**
 * Outcome of a rejudge request. {@code requeued} is how many submissions were reset and put back
 * on the queue; {@code scope} is "submission" | "problem" | "contest" and {@code targetId} the id
 * that was addressed. Judging happens asynchronously — the board catches up as verdicts land.
 */
public record RejudgeResult(String scope, long targetId, int requeued) {
}
