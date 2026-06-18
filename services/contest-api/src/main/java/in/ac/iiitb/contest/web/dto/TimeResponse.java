package in.ac.iiitb.contest.web.dto;

/** Server clock for the contest countdown. The frontend uses epochMillis, never the browser clock. */
public record TimeResponse(String serverTime, long epochMillis) {
}
