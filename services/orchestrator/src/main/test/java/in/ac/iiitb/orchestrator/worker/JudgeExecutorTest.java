package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The concurrency contract of the P0-1 pool, isolated from Redis and Judge0. These pin the guarantees the
 * whole fix rests on: judging is bounded at the pool size, a reserved slot is ALWAYS returned (success,
 * throw, or shutdown-rejection), reserve() applies backpressure by blocking when saturated and is
 * interruptible for shutdown, and close() drains in-flight judges.
 */
class JudgeExecutorTest {

    /** Poll a condition up to a timeout without sleeping the whole budget. */
    private static boolean awaitTrue(AtomicBoolean flag, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (flag.get()) {
                return true;
            }
            Thread.sleep(2);
        }
        return flag.get();
    }

    @Test
    void reserveReturnsBetweenOneAndMaxAndNeverExceedsCapacity() throws Exception {
        try (JudgeExecutor ex = new JudgeExecutor(4, 1000)) {
            assertThat(ex.reserve(1)).isEqualTo(1);
            ex.releaseUnused(1);
            assertThat(ex.reserve(3)).isEqualTo(3);
            ex.releaseUnused(3);
            assertThat(ex.reserve(100)).isEqualTo(4);   // capped at capacity, never more
            ex.releaseUnused(4);
            assertThat(ex.reserve(0)).isEqualTo(1);      // floored at one
            ex.releaseUnused(1);
            assertThat(ex.availablePermits()).isEqualTo(4);
        }
    }

    @Test
    void concurrencyIsBoundedAtCapacityAndActuallyReachesIt() throws Exception {
        final int cap = 4;
        final int total = cap * 6;                       // multiple of cap so every batch is a full house
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicBoolean everExceeded = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(total);
        CyclicBarrier fullHouse = new CyclicBarrier(cap);

        try (JudgeExecutor ex = new JudgeExecutor(cap, 2000)) {
            int dispatched = 0;
            while (dispatched < total) {
                int slots = ex.reserve(cap);             // blocks once saturated -> backpressure
                int n = Math.min(slots, total - dispatched);
                for (int i = 0; i < n; i++) {
                    ex.dispatch(() -> {
                        int now = inFlight.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        if (now > cap) {
                            everExceeded.set(true);
                        }
                        try {
                            fullHouse.await(2, TimeUnit.SECONDS);   // hold until cap tasks rendezvous
                        } catch (InterruptedException | BrokenBarrierException | java.util.concurrent.TimeoutException ignored) {
                            // fine — tail behaviour, not what we're asserting
                        }
                        inFlight.decrementAndGet();
                        done.countDown();
                    });
                }
                ex.releaseUnused(slots - n);
                dispatched += n;
            }
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(everExceeded).as("in-flight judges must never exceed the pool size").isFalse();
        assertThat(peak.get()).as("the pool should actually be driven to full capacity").isEqualTo(cap);
    }

    @Test
    void permitIsReleasedAfterASuccessfulTask() throws Exception {
        try (JudgeExecutor ex = new JudgeExecutor(2, 1000)) {
            CountDownLatch ran = new CountDownLatch(1);
            assertThat(ex.reserve(1)).isEqualTo(1);
            ex.dispatch(ran::countDown);
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
            // give the finally a beat to release, then assert full capacity is back
            AtomicBoolean back = new AtomicBoolean();
            //noinspection StatementWithEmptyBody
            for (int i = 0; i < 500 && ex.availablePermits() != 2; i++) { Thread.sleep(2); }
            back.set(ex.availablePermits() == 2);
            assertThat(back).isTrue();
        }
    }

    @Test
    void permitIsReleasedEvenWhenTheTaskThrows() throws Exception {
        try (JudgeExecutor ex = new JudgeExecutor(2, 1000)) {
            CountDownLatch ran = new CountDownLatch(1);
            assertThat(ex.reserve(1)).isEqualTo(1);
            ex.dispatch(() -> {
                try {
                    throw new RuntimeException("judge blew up");
                } finally {
                    ran.countDown();
                }
            });
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 500 && ex.availablePermits() != 2; i++) { Thread.sleep(2); }
            assertThat(ex.availablePermits()).as("a throwing judge must not leak its slot").isEqualTo(2);
        }
    }

    @Test
    void reserveBlocksWhileSaturatedThenProceedsWhenASlotFrees() throws Exception {
        try (JudgeExecutor ex = new JudgeExecutor(2, 1000)) {
            CountDownLatch hold = new CountDownLatch(1);
            // occupy both slots with tasks that block until we let them go
            for (int i = 0; i < 2; i++) {
                ex.reserve(1);
                ex.dispatch(() -> {
                    try { hold.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                });
            }
            AtomicBoolean reserved = new AtomicBoolean(false);
            Thread waiter = new Thread(() -> {
                try {
                    ex.reserve(1);          // must block: 0 permits free
                    reserved.set(true);
                    ex.releaseUnused(1);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "reserve-waiter");
            waiter.start();

            assertThat(awaitTrue(reserved, 200)).as("reserve must block while saturated").isFalse();
            hold.countDown();               // free the slots
            assertThat(awaitTrue(reserved, 2000)).as("reserve must proceed once a slot frees").isTrue();
            waiter.join(2000);
        }
    }

    @Test
    void reserveIsInterruptibleForShutdown() throws Exception {
        try (JudgeExecutor ex = new JudgeExecutor(1, 1000)) {
            CountDownLatch hold = new CountDownLatch(1);
            ex.reserve(1);
            ex.dispatch(() -> {
                try { hold.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            });

            AtomicBoolean interrupted = new AtomicBoolean(false);
            Thread waiter = new Thread(() -> {
                try {
                    ex.reserve(1);          // blocks: the only permit is held
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            }, "reserve-interruptible");
            waiter.start();
            Thread.sleep(100);
            waiter.interrupt();             // the shutdown signal
            assertThat(awaitTrue(interrupted, 2000)).as("a blocked reserve must unblock on interrupt").isTrue();
            waiter.join(2000);
            hold.countDown();
        }
    }

    @Test
    void dispatchAfterCloseIsRejectedAndReturnsTheSlot() throws Exception {
        JudgeExecutor ex = new JudgeExecutor(2, 1000);
        ex.close();
        int slots = ex.reserve(1);          // the semaphore still hands out permits after close
        assertThat(slots).isEqualTo(1);

        boolean rejected = false;
        try {
            ex.dispatch(() -> { });
        } catch (RejectedExecutionException expected) {
            rejected = true;
        }
        assertThat(rejected).as("dispatch after close must be rejected").isTrue();
        assertThat(ex.availablePermits()).as("a rejected dispatch must not leak the slot").isEqualTo(2);
    }

    @Test
    void closeDrainsInFlightJudges() throws Exception {
        JudgeExecutor ex = new JudgeExecutor(2, 2000);
        AtomicBoolean finished = new AtomicBoolean(false);
        ex.reserve(1);
        ex.dispatch(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            finished.set(true);
        });
        ex.close();                          // must block until the in-flight judge completes
        assertThat(finished).as("close must wait for in-flight judges").isTrue();
        assertThat(ex.availablePermits()).isEqualTo(2);
    }

    @Test
    void closeIsIdempotent() {
        JudgeExecutor ex = new JudgeExecutor(2, 500);
        ex.close();
        ex.close();                          // second call must be a harmless no-op
        assertThat(ex.availablePermits()).isEqualTo(2);
    }

    @Test
    void activeCountReflectsReservedAndInFlight() throws Exception {
        try (JudgeExecutor ex = new JudgeExecutor(3, 1000)) {
            assertThat(ex.activeCount()).isZero();
            int got = ex.reserve(2);
            assertThat(got).isEqualTo(2);
            assertThat(ex.activeCount()).isEqualTo(2);   // reserved-but-not-dispatched counts as active
            ex.releaseUnused(2);
            assertThat(ex.activeCount()).isZero();
        }
    }
}
