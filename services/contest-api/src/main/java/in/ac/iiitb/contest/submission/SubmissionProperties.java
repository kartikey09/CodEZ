package in.ac.iiitb.contest.submission;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Admission-control knobs (bound from app.submission in application.yml).
 *   allowedLanguages  - the only language slugs accepted
 *   maxSourceBytes    - hard cap on UTF-8 source size (64 KB)
 *   cooldownMs         - minimum gap between a user's submissions
 *   inflightTtlSeconds - safety TTL on the "one submission judging at a time" lock
 *                        (the orchestrator clears it on verdict; the TTL guards against a dead worker)
 *   graceSeconds       - leeway past ends_at so an in-flight click at the buzzer still lands
 *   streamKey          - the Redis stream the orchestrator consumes
 *
 * Validated: an empty allowedLanguages would reject every submission, and a zeroed maxSourceBytes would
 * reject every non-empty one -- both silent if the key were simply missing.
 */
@Validated
@ConfigurationProperties(prefix = "app.submission")
public record SubmissionProperties(
        @NotEmpty List<String> allowedLanguages,
        @Positive int maxSourceBytes,
        /** 0 is legitimate: no minimum gap between a user's submissions. */
        @PositiveOrZero long cooldownMs,
        @Positive long inflightTtlSeconds,
        /** 0 is legitimate: no leeway past ends_at. */
        @PositiveOrZero long graceSeconds,
        @NotBlank String streamKey) {
}
