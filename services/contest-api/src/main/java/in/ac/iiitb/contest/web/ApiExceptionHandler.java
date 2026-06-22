package in.ac.iiitb.contest.web;

import in.ac.iiitb.contest.error.*;
import in.ac.iiitb.contest.web.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(ContestNotStartedException.class)
    public ResponseEntity<ErrorResponse> notStarted() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("CONTEST_NOT_STARTED", "The contest has not started yet"));
    }

    @ExceptionHandler(NoContestFoundException.class)
    public ResponseEntity<ErrorResponse> noContestFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NO_CONTEST_FOUND", "No contest found"));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", "Malformed request body"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", "Resource already exists or violates a unique constraint"));
    }

    // ----- submission admission rejections -----

    @ExceptionHandler(LanguageNotAllowedException.class)
    public ResponseEntity<ErrorResponse> badLanguage() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("LANGUAGE_NOT_ALLOWED", "That language is not accepted for this contest"));
    }

    @ExceptionHandler(SourceTooLargeException.class)
    public ResponseEntity<ErrorResponse> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorResponse("SOURCE_TOO_LARGE", "Source exceeds the size limit"));
    }

    @ExceptionHandler(ContestEndedException.class)
    public ResponseEntity<ErrorResponse> ended() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("CONTEST_ENDED", "The contest window has closed"));
    }

    @ExceptionHandler(SubmissionInFlightException.class)
    public ResponseEntity<ErrorResponse> inFlight() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("SUBMISSION_IN_FLIGHT", "A previous submission is still being judged"));
    }

    @ExceptionHandler(CooldownActiveException.class)
    public ResponseEntity<ErrorResponse> cooldown() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("COOLDOWN_ACTIVE", "Please wait before submitting again"));
    }

    @ExceptionHandler(SubmissionDatabaseException.class)
    public ResponseEntity<ErrorResponse> SubmissionDatabaseException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("DB FAILED TO SAVE", e.getMessage()));
    }
}
