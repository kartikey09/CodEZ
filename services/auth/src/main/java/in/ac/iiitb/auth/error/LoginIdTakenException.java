package in.ac.iiitb.auth.error;

/** A user with the requested login id already exists → 409. */
public class LoginIdTakenException extends RuntimeException {
    public LoginIdTakenException(String loginId) {
        super("Login id already taken: " + loginId);
    }
}
