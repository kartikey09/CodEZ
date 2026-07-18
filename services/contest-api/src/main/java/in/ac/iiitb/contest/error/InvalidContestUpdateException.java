package in.ac.iiitb.contest.error;

/**
 * A contest update was rejected → 400: an unknown state, or an end that isn't after
 * the start. Carries a specific message so the admin UI can show what was wrong.
 */
public class InvalidContestUpdateException extends RuntimeException {
    public InvalidContestUpdateException(String message) {
        super(message);
    }
}
