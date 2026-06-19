package in.ac.iiitb.contest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateContestRequest(
    @NotBlank String title,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotBlank String state) {   // draft | published | running | finished
}
