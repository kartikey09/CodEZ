package in.ac.iiitb.contest.error;

/**
 * A problem edit or test-case change was rejected → 400: a non-positive limit, a blank field, or a
 * duplicate test ordinal.
 */
public class InvalidProblemUpdateException extends RuntimeException {
    public InvalidProblemUpdateException(String message) {
        super(message);
    }
}
