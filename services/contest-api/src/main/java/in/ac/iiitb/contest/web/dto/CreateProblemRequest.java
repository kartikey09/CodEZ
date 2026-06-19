package in.ac.iiitb.contest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProblemRequest(
        @NotNull Long contestId,
        @NotBlank String label,
        @NotBlank String title,
        @NotBlank String statementMd,
        int timeLimitMs,        // <=0 -> defaulted to 1000 in the controller
        int memoryLimitMb) {    // <=0 -> defaulted to 256
}
