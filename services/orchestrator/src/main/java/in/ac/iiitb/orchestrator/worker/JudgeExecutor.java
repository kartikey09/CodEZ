package in.ac.iiitb.orchestrator.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bounded pool for running judge jobs concurrently. This is the whole of the P0-1 fix: the judging
 * pipeline is ~99% I/O wait on Judge0, so the single-threaded Day-7 loop left the platform judging one
 * submission at a time no matter how much Judge0 capacity was behind it. This turns that one thread into
 * up to {@code concurrency} in-flight judges, backed by virtual threads (JDK 21) since each judge mostly
 * blocks on HTTP + poll-sleep.
 *
 * The contract is a three-step handshake so the read loop never pulls more records off the stream than it
 * can actually run — Redis Streams keeps unacked records in the PEL, so over-reading would just pile up
 * pending entries and defeat the reclaimer's "the owner looks dead" heuristic:
 *
 *   1. {@link #reserve(int)} — block until at least one slot is free, then greedily take up to {@code max}.
 *   2. {@link #dispatch(Runnable)} — hand exactly one reserved slot to a task; the slot is returned when the
 *      task finishes, whether it returns normally OR throws.
 *   3. {@link #releaseUnused(int)} — give back any reserved slots the caller didn't dispatch (e.g. a BLOCK
 *      read that returned fewer records than reserved).
 *
 * Invariant: the number of permits in circulation is always exactly {@code concurrency}, so the number of
 * judges running at once can never exceed it. The permit a task holds is released in a {@code finally}, so
 * a task that throws (or a pool that rejects on shutdown) can never leak a slot.
 *
 * Shutdown ({@link #close()}) is idempotent and drains: it stops accepting work and waits up to
 * {@code drainTimeoutMs} for in-flight judges to finish before forcing termination. The worker calls this
 * from its lifecycle stop() while Redis/Postgres are still up, so an in-flight judge can still ACK and
 * write its verdict during a clean shutdown instead of being abandoned to the reclaimer.
 */
public class JudgeExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JudgeExecutor.class);

    private final int capacity;
    private final long drainTimeoutMs;
    private final Semaphore permits;
    private final ExecutorService pool;
    private volatile boolean closed = false;

    public JudgeExecutor(int concurrency, long drainTimeoutMs) {
        this.capacity = Math.max(1, concurrency);
        this.drainTimeoutMs = Math.max(0, drainTimeoutMs);
        this.permits = new Semaphore(this.capacity);
        this.pool = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Block until at least one slot is free, then take up to {@code max} slots (never more than the pool's
     * total capacity). Returns how many were reserved — always {@literal >=} 1. The caller owns these slots
     * until it dispatches or releases them.
     *
     * @throws InterruptedException if interrupted while waiting for the first slot (the shutdown signal).
     */
    public int reserve(int max) throws InterruptedException {
        int want = Math.max(1, Math.min(max, capacity));
        permits.acquire();                 // block for the first slot
        int got = 1;
        while (got < want && permits.tryAcquire()) {   // then grab whatever else is free, without blocking
            got++;
        }
        return got;
    }

    /**
     * Run {@code task} on the pool, consuming one reserved slot. The slot is released after the task finishes,
     * success or failure. Must be preceded by a {@link #reserve(int)} that reserved the slot being consumed.
     *
     * @throws RejectedExecutionException if the pool is shutting down; the slot is returned before throwing,
     *         and the caller should stop dispatching the rest of its batch (those records stay pending and
     *         are recovered by the reclaimer).
     */
    public void dispatch(Runnable task) {
        try {
            pool.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    // The processor already handles its own errors; this is belt-and-suspenders so a slot
                    // is never leaked even if a task throws something entirely unexpected.
                    log.error("judge task threw unexpectedly", t);
                } finally {
                    permits.release();
                }
            });
        } catch (RejectedExecutionException rejected) {
            permits.release();             // pool closed between reserve and dispatch — don't leak the slot
            throw rejected;
        }
    }

    /** Return slots reserved but not dispatched (a short/empty read, or a batch cut off by shutdown). */
    public void releaseUnused(int n) {
        if (n > 0) {
            permits.release(n);
        }
    }

    /** Max concurrent judges. */
    public int capacity() {
        return capacity;
    }

    /** Slots currently free (capacity minus in-flight-plus-reserved). Primarily for metrics/tests. */
    public int availablePermits() {
        return permits.availablePermits();
    }

    /** Judges currently running plus slots reserved but not yet dispatched. Primarily for metrics/tests. */
    public int activeCount() {
        return capacity - permits.availablePermits();
    }

    /**
     * Stop accepting work and drain. Idempotent. Waits up to {@code drainTimeoutMs} for in-flight judges to
     * finish, then forces shutdown. Spring also invokes this on context close (AutoCloseable), which is why
     * it must tolerate being called twice.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(drainTimeoutMs, TimeUnit.MILLISECONDS)) {
                log.warn("judge pool did not drain within {} ms; forcing shutdown", drainTimeoutMs);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
