package in.ac.iiitb.contest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitRequest(
        @NotNull Long problemId,
        @NotBlank String language,
        @NotBlank String sourceCode) {
}
