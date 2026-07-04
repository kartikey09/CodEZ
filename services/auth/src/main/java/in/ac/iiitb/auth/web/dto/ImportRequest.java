package in.ac.iiitb.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Bulk roster import. {@code csv} is the raw file text; columns are
 * {@code loginId,displayName,role} (role optional, defaults "student"). A header row
 * (first cell "loginId"/"login_id", case-insensitive) is detected and skipped.
 */
public record ImportRequest(
        @NotBlank String csv) {
}
