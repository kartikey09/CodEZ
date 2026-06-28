package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

class Judge0CircuitBreakerTest {

    /** Hand-cranked clock so the open-window can be advanced without sleeping. */
    private static final class FakeClock implements LongSupplier {
        long now = 0;
        public long getAsLong() {
            return now;
        }
    }

    @Test
    void closedByDefaultAndAllowsRequests() {
        Judge0CircuitBreaker cb = new Judge0CircuitBreaker(3, 1000, new FakeClock());
        assertThat(cb.state()).isEqualTo(Judge0CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void opensOnlyAfterTheFailureThreshold() {
        Judge0CircuitBreaker cb = new Judge0CircuitBreaker(3, 1000, new FakeClock());
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(Judge0CircuitBreaker.State.CLOSED);   // 2 < 3
        assertThat(cb.allowRequest()).isTrue();
        cb.recordFailure();                                                     // 3rd -> open
        assertThat(cb.state()).isEqualTo(Judge0CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();                               // refused inside the window
    }

    @Test
    void allowsOneProbePerWindowAndClosesOnSuccess() {
        FakeClock clock = new FakeClock();
        Judge0CircuitBreaker cb = new Judge0CircuitBreaker(2, 1000, clock);
        cb.recordFailure();
        cb.recordFailure();                       // open
        assertThat(cb.allowRequest()).isFalse();

        clock.now = 1000;                         // window elapsed
        assertThat(cb.allowRequest()).isTrue();   // exactly one probe granted
        assertThat(cb.allowRequest()).isFalse();  // ...and only one

        cb.recordSuccess();                       // probe succeeded
        assertThat(cb.state()).isEqualTo(Judge0CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.failureCount()).isZero();
    }

    @Test
    void aFailedProbeReopensAndDefersTheNextProbe() {
        FakeClock clock = new FakeClock();
        Judge0CircuitBreaker cb = new Judge0CircuitBreaker(1, 1000, clock);
        cb.recordFailure();                       // threshold 1 -> open immediately

        clock.now = 1000;
        assertThat(cb.allowRequest()).isTrue();   // probe
        cb.recordFailure();                       // probe failed -> stay open, push next probe to 2000
        assertThat(cb.state()).isEqualTo(Judge0CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();  // still within the (pushed) window

        clock.now = 2000;
        assertThat(cb.allowRequest()).isTrue();   // next probe granted
    }
}
