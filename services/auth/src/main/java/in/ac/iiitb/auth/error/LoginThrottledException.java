package in.ac.iiitb.auth.error;

/**
 * Too many failed logins for this account or from this address → 429. Carries the remaining
 * lockout in seconds so the client can say something useful instead of "try again later".
 */
public class LoginThrottledException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginThrottledException(long retryAfterSeconds) {
        super("Too many failed login attempts. Try again in " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
