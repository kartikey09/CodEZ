package in.ac.iiitb.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Change a user's role. Must be "student" or "admin". */
public record SetRoleRequest(
        @NotBlank String role) {
}
