package in.ac.iiitb.contest.web.dto;

import jakarta.validation.constraints.NotNull;

public record TestCaseUpload(
        @NotNull Integer ordinal,
        @NotNull String input,
        @NotNull String expectedOutput,
        boolean sample) {
}
