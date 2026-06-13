package in.ac.iiitb.auth.web.dto;

public record MeResponse(
        long userId,
        String loginId,
        String displayName,
        String role,
        boolean mustChangePassword) {
}
