package in.ac.iiitb.contest.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/problems/{id}/run body — problemId comes from the path, not the body. */
public record RunRequest(
        @NotBlank String language,
        @NotBlank String sourceCode) {
}
