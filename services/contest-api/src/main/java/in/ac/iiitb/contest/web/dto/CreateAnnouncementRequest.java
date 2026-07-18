package in.ac.iiitb.contest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Post a new announcement to a contest. */
public record CreateAnnouncementRequest(
        @NotNull Long contestId,
        @NotBlank String message) {
}
