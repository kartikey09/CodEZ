package in.ac.iiitb.contest.web.dto;

public record ErrorResponse(String code, String message, Integer retryAfterSeconds) {

    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
