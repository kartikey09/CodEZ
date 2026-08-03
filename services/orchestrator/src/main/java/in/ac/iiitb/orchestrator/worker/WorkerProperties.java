package in.ac.iiitb.orchestrator.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 */
@ConfigurationProperties(prefix = "app.worker")
public record WorkerProperties(
    String streamKey,
    String group,
    String consumer,
    long blockMs,
    int batchCount,
    long pollInitialBackoffMs,
    long pollMaxBackoffMs,
    long pollMaxWaitMs,
    int compileOutputMaxBytes,
    String inflightKeyPrefix,
    String userChannelPrefix,
    int batchSize,
    long reclaimIntervalMs,
    long reclaimMinIdleMs,
    int reclaimBatch,
    int maxDeliveries,
    int breakerFailureThreshold,
    long breakerOpenMs,
    long breakerPauseMs,
    int judgeConcurrency,
    long drainTimeoutMs,
    int testCacheMaxEntries,
    long testCacheExpireAfterAccessMs,
    boolean submitEarlyExit,
    long inflightTtlSeconds) {
}
