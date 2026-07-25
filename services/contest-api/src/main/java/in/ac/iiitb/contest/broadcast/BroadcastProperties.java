package in.ac.iiitb.contest.broadcast;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.broadcast.*. contest-api listens to the worker's verdict pings on {@code verdictChannelPattern}
 * and, after recomputing, publishes the refreshed board on {@code standingsChannelPrefix}{contestId} for the
 * realtime service to relay. {@code debounceMs} coalesces a burst of verdicts in one contest into a single
 * recompute; {@code broadcastLimit} caps the rows pushed. {@code maxWaitMs} caps how long a sustained
 * burst can defer a broadcast: even if verdicts keep arriving, the board is forced out once it has gone
 * this long without an update.
 */
@ConfigurationProperties(prefix = "app.broadcast")
public record BroadcastProperties(
        String verdictChannelPattern,
        String standingsChannelPrefix,
        long debounceMs,
        long maxWaitMs,
        int broadcastLimit) {
}
