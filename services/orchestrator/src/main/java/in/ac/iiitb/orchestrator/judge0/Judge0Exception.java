package in.ac.iiitb.orchestrator.judge0;

/** Raised when Judge0 can't be reached or keeps failing after the client's retries. */
public class Judge0Exception extends RuntimeException {

    public Judge0Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
