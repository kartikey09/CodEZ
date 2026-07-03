package in.ac.iiitb.realtime.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.realtime.*. Channel names mirror what the worker publishes (ch:user:*) and what contest-api
 * publishes (ch:standings:*); the snapshot key prefix mirrors contest-api's scoring key prefix so a
 * freshly-connected client can be handed the current board immediately.
 */
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        String verdictChannelPattern,
        String verdictChannelPrefix,
        String standingsChannelPattern,
        String standingsChannelPrefix,
        String snapshotKeyPrefix,
        long sessionTtlHours,
        List<String> allowedOriginPatterns,
        long sendTimeLimitMs,
        int sendBufferBytes) {
}
