package in.ac.iiitb.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create one account. {@code role} defaults to "student" when blank. {@code password}
 * is optional: leave it blank and the server generates a random one (returned once in
 * the response). Every created account starts must-change-on-first-login.
 */
public record CreateUserRequest(
        @NotBlank String loginId,
        @NotBlank String displayName,
        String role,
        String password) {
}
