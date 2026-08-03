package in.ac.iiitb.orchestrator.judge0;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds app.judge0.*. Validated so a missing timeout can't bind to 0, which the underlying request factory
 * reads as "wait forever" -- a hung Judge0 would then park a judging thread indefinitely.
 *
 * {@code authToken} is @NotNull rather than @NotBlank on purpose: an empty token is the valid way to talk
 * to a Judge0 with authentication disabled, and {@link Judge0Client} omits the header when it's blank.
 */
@Validated
@ConfigurationProperties(prefix = "app.judge0")
public record Judge0Properties(
        @NotBlank String baseUrl,
        @NotNull String authToken,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs,
        @Positive int maxRetries) {
}
