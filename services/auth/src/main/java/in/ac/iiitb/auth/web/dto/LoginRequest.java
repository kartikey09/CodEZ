package in.ac.iiitb.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String loginId,
    @NotBlank String password) {
}
