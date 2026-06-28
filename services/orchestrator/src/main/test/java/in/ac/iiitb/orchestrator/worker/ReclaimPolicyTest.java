package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The reclaimer's decision boundaries, isolated from Redis. The XPENDING/XCLAIM plumbing is exercised
 * by the manual chaos matrix in the runbook; here we just pin the two thresholds that decide a record's
 * fate so a careless change to either comparison gets caught.
 */
class ReclaimPolicyTest {

    @Test
    void poisonOnlyOnceDeliveriesExceedTheCap() {
        int cap = 3;
        assertThat(Reclaimer.isPoison(1, cap)).isFalse();
        assertThat(Reclaimer.isPoison(3, cap)).isFalse();   // the 3rd attempt is still allowed
        assertThat(Reclaimer.isPoison(4, cap)).isTrue();    // a 4th delivery means it's wedged -> IE
    }

    @Test
    void claimOnlyWhenIdleAtLeastTheThreshold() {
        long minIdle = 90_000;
        assertThat(Reclaimer.shouldClaim(0, minIdle)).isFalse();          // fresh — its owner is alive
        assertThat(Reclaimer.shouldClaim(89_999, minIdle)).isFalse();
        assertThat(Reclaimer.shouldClaim(90_000, minIdle)).isTrue();      // exactly idle enough
        assertThat(Reclaimer.shouldClaim(120_000, minIdle)).isTrue();     // long dead — steal it
    }
}
