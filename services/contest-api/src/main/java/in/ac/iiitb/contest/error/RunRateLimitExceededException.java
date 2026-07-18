package in.ac.iiitb.contest.error;

/** Thrown when a user exceeds the "Run" feature's own rate limit (independent of Submit's cooldown). */
public class RunRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RunRateLimitExceededException(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
