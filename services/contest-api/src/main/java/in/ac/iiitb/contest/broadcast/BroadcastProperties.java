package in.ac.iiitb.contest.broadcast;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds app.broadcast.*. contest-api listens to the worker's verdict pings on {@code verdictChannelPattern}
 * and, after recomputing, publishes the refreshed board on {@code standingsChannelPrefix}{contestId} for the
 * realtime service to relay. {@code debounceMs} coalesces a burst of verdicts in one contest into a single
 * recompute; {@code broadcastLimit} caps the rows pushed. {@code maxWaitMs} caps how long a sustained
 * burst can defer a broadcast: even if verdicts keep arriving, the board is forced out once it has gone
 * this long without an update.
 *
 * VALIDATION: {@code maxWaitMs} is why this exists. It shipped missing from application.yml, bound to 0,
 * and silently collapsed the debounce to a zero delay -- every verdict forced a full board rebuild, the
 * exact burst the throttle was written to prevent. @Positive turns that omission into a startup failure.
 */
@Validated
@ConfigurationProperties(prefix = "app.broadcast")
public record BroadcastProperties(
        @NotBlank String verdictChannelPattern,
        @NotBlank String standingsChannelPrefix,
        /** 0 is legitimate: broadcast on every verdict, no coalescing. */
        @PositiveOrZero long debounceMs,
        @Positive long maxWaitMs,
        @Positive int broadcastLimit) {
}
