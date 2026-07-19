package in.ac.iiitb.contest.error;

/**
 * A rejudge was refused → 400: the submission isn't judged yet, it's a practice Run row, or the
 * batch is larger than the configured cap.
 */
public class InvalidRejudgeException extends RuntimeException {
    public InvalidRejudgeException(String message) {
        super(message);
    }
}
