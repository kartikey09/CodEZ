package in.ac.iiitb.contest.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GateProbeTest {
    @Test
    void thisFailsOnPurpose() {
        assertEquals(1, 2, "intentional failure — proving the CI gate blocks merges");
    }
}
