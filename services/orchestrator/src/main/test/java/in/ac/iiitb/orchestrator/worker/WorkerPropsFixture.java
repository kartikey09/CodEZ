package in.ac.iiitb.orchestrator.worker;

/**
 * A WorkerProperties with production-shaped defaults, so a test that cares about one knob doesn't
 * have to spell out the other twenty-two. Polling backoffs are near-zero: the unit tests drive stub
 * Judge0 clients that finish on the first poll, so real backoffs would only add wall-clock time.
 */
final class WorkerPropsFixture {

    private WorkerPropsFixture() {
    }

    static WorkerProperties defaults() {
        return withBatchSize(1);
    }

    /** batchSize 1 selects JudgeService's sequential path; >1 selects the batched one. */
    static WorkerProperties withBatchSize(int batchSize) {
        return withBatchSize(batchSize, true);
    }

    /**
     * P0-3: {@code submitEarlyExit} false restores the old exhaustive behaviour for Submit jobs, so a test
     * can pin either policy without spelling out the other twenty-two knobs.
     */
    static WorkerProperties withBatchSize(int batchSize, boolean submitEarlyExit) {
        return new WorkerProperties(
                "subq", "workers", "test",
                /* blockMs */            5000,
                /* batchCount */         10,
                /* pollInitialBackoff */ 1,
                /* pollMaxBackoff */     1,
                /* pollMaxWait */        1000,
                /* compileOutputMax */   8192,
                /* inflightKeyPrefix */  "inflight:",
                /* userChannelPrefix */  "ch:user:",
                batchSize,
                /* reclaimIntervalMs */  30000,
                /* reclaimMinIdleMs */   90000,
                /* reclaimBatch */       50,
                /* maxDeliveries */      3,
                /* breakerFailureThreshold */ 5,
                /* breakerOpenMs */      15000,
                /* breakerPauseMs */     1000,
                /* judgeConcurrency */   4,
                /* drainTimeoutMs */     5000,
                /* testCacheMaxEntries */ 500,
                /* testCacheExpireAfterAccessMs */ 3_600_000,
                submitEarlyExit,
                /* inflightTtlSeconds */ 120);
    }
}
