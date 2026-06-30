package in.ac.iiitb.orchestrator.worker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * A minimal circuit breaker that protects Judge0 from a thundering herd when it's down, and \u2014 just
 * as importantly \u2014 stops the worker mass-failing submissions during an outage.
 *
 * Two states:
 *   CLOSED  \u2014 normal; every request is allowed.
 *   OPEN    \u2014 Judge0 is presumed down; requests are refused, except that once every breakerOpenMs the
 *             breaker allows a single "probe" request through. A probe that succeeds closes the breaker;
 *             a probe that fails pushes the next probe another breakerOpenMs out.
 *
 * The breaker only counts *transport* failures (Judge0Exception) \u2014 a WA/TLE/CE is a normal verdict,
 * not a Judge0 outage. Methods are synchronized so the read loop and the reclaimer thread can share one
 * instance safely. The clock is injectable so the state machine is unit-testable without sleeping.
 */
public class Judge0CircuitBreaker {

    public enum State { CLOSED, OPEN }

    private final int failureThreshold;
    private final long openMs;
    private final LongSupplier clock;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long nextProbeAt = 0L;

    public Judge0CircuitBreaker(int failureThreshold, long openMs, LongSupplier clock) {
        this.failureThreshold = Math.max(1, failureThreshold);  //5
        this.openMs = openMs;   //15 sec
        this.clock = clock;
    }

    /** True if a request may proceed now. When OPEN, grants exactly one probe per openMs window. */
    public synchronized boolean allowRequest() {
        if (state.get() == State.CLOSED) {
            return true;
        }
        long now = clock.getAsLong();
        if (now >= nextProbeAt) {
            nextProbeAt = now + openMs;   // schedule the next probe; this one is allowed through
            return true;
        }
        return false;
    }

    /** A real judging success \u2014 reset everything and close. */
    public synchronized void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    /** A Judge0 transport failure \u2014 open after the threshold, or (if a probe failed) push the next probe out. */
    public synchronized void recordFailure() {
        int f = consecutiveFailures.incrementAndGet();
        if (state.get() == State.OPEN) {
            nextProbeAt = clock.getAsLong() + openMs;   // probe failed; stay open, wait again
            return;
        }
        if (f >= failureThreshold) {
            state.set(State.OPEN);
            nextProbeAt = clock.getAsLong() + openMs;
        }
    }

    public State state() {
        return state.get();
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public int failureCount() {
        return consecutiveFailures.get();
    }
}
