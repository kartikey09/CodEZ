package in.ac.iiitb.realtime.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds app.realtime.*. Channel names mirror what the worker publishes (ch:user:*) and what contest-api
 * publishes (ch:standings:*); the snapshot key prefix mirrors contest-api's scoring key prefix so a
 * freshly-connected client can be handed the current board immediately.
 *
 * Validated: these channel names have to agree with what the worker and contest-api publish, so a missing
 * key binding to null (or a zeroed send limit, which would stall every WebSocket write) should stop the
 * service at startup rather than surface later as "the client just never gets updates".
 */
@Validated
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        @NotBlank String verdictChannelPattern,
        @NotBlank String verdictChannelPrefix,
        @NotBlank String standingsChannelPattern,
        @NotBlank String standingsChannelPrefix,
        @NotBlank String snapshotKeyPrefix,
        @Positive long sessionTtlHours,
        @NotEmpty List<String> allowedOriginPatterns,
        @Positive long sendTimeLimitMs,
        @Positive int sendBufferBytes) {
}
