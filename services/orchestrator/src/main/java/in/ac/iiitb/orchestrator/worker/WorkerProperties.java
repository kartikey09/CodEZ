package in.ac.iiitb.orchestrator.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Worker configuration (app.worker). Day-7 fields plus the Day-8 robustness knobs:
 * batched judging, the reclaimer sweep, the poison-pill cap, and the Judge0 circuit breaker.
 *
 * P0-1 concurrency knobs:
 *   judgeConcurrency - max submissions judged in parallel (the {@link JudgeExecutor} pool size). Size it to
 *                      roughly 2x the Judge0 worker count: judging is I/O-bound, so a small multiple keeps
 *                      Judge0's workers busy without overwhelming it.
 *   drainTimeoutMs    - on shutdown, how long to let in-flight judges finish before forcing the pool down.
 *
 * P0-3: submitEarlyExit -- stop judging a SUBMIT at the first failing test (ICPC "failed on test k"),
 *       instead of running every test. Runs are always exhaustive (they need the full sample breakdown).
 * P0-4: inflightTtlSeconds -- TTL the worker re-takes on the one-in-flight lock at markRunning, so a long
 *       queue wait doesn't eat into the judging window. Should match contest-api's app.submission value.
 *
 * VALIDATION: constructor binding fills an absent property with the primitive default -- 0 for a number,
 * false for a boolean -- so a typo'd or forgotten key is silently "configured" rather than rejected. That
 * has already bitten this project once (app.broadcast.max-wait-ms shipped missing, which zeroed the
 * standings debounce). The constraints below turn every such omission into a startup failure naming the
 * offending key. Booleans are boxed for the same reason: only a wrapper can tell "absent" from "false".
 */
@Validated
@ConfigurationProperties(prefix = "app.worker")
public record WorkerProperties(
    @NotBlank String streamKey,
    @NotBlank String group,
    @NotBlank String consumer,
    @Positive long blockMs,
    @Positive int batchCount,
    @Positive long pollInitialBackoffMs,
    @Positive long pollMaxBackoffMs,
    @Positive long pollMaxWaitMs,
    @Positive int compileOutputMaxBytes,
    @NotBlank String inflightKeyPrefix,
    @NotBlank String userChannelPrefix,
    @Positive int batchSize,
    @Positive long reclaimIntervalMs,
    @Positive long reclaimMinIdleMs,
    @Positive int reclaimBatch,
    @Positive int maxDeliveries,
    @Positive int breakerFailureThreshold,
    @Positive long breakerOpenMs,
    @Positive long breakerPauseMs,
    @Positive int judgeConcurrency,
    /** 0 is legitimate: force the pool down immediately instead of draining. */
    @PositiveOrZero long drainTimeoutMs,
    @Positive int testCacheMaxEntries,
    @Positive long testCacheExpireAfterAccessMs,
    @NotNull Boolean submitEarlyExit,
    @Positive long inflightTtlSeconds) {
}
