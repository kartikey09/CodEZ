package in.ac.iiitb.auth.web;

import in.ac.iiitb.auth.error.*;
import in.ac.iiitb.auth.web.dto.ErrorResponse;
import in.ac.iiitb.auth.error.LoginThrottledException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalidCredentials() {
        // Same response for wrong password and unknown id — no account enumeration.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_CREDENTIALS", "Invalid login id or password"));
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ErrorResponse> disabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("ACCOUNT_DISABLED", "This account is disabled. Contact the admin."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(CurrentPasswordIncorrectException.class)
    public ResponseEntity<ErrorResponse> currentPasswordIncorrect() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("CURRENT_PASSWORD_INCORRECT", "Current password is incorrect"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", msg));
    }

    @ExceptionHandler(LoginIdTakenException.class)
    public ResponseEntity<ErrorResponse> loginIdTaken() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("LOGIN_ID_TAKEN", "A user with that login id already exists"));
    }
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> userNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", "No such user"));
    }
    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ErrorResponse> invalidRole() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_ROLE", "Role must be 'student' or 'admin'"));
    }
    @ExceptionHandler(SelfModificationException.class)
    public ResponseEntity<ErrorResponse> selfModification() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("SELF_MODIFICATION", "You cannot deactivate or demote your own account"));
    }

    // ----- Day 15: login throttling -----

    /**
     * 429 with Retry-After, so a client (and a human) can tell a lockout apart from a bad password.
     * This does not leak account existence: the throttle counts the submitted login id whether or
     * not it resolves, so an unknown id locks out exactly the same way.
     */
    @ExceptionHandler(LoginThrottledException.class)
    public ResponseEntity<ErrorResponse> throttled(LoginThrottledException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", Long.toString(ex.retryAfterSeconds()))
            .body(new ErrorResponse("LOGIN_THROTTLED", ex.getMessage()));
    }
}
