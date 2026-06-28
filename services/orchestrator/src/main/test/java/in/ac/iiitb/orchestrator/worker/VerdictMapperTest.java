package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerdictMapperTest {

    private static final int LIMIT_KB = 262144;   // 256 MB

    @Test
    void mapsTheStableStatusTable() {
        assertThat(VerdictMapper.map(3, 1000, LIMIT_KB)).isEqualTo(Verdict.AC);
        assertThat(VerdictMapper.map(4, 1000, LIMIT_KB)).isEqualTo(Verdict.WA);
        assertThat(VerdictMapper.map(5, 1000, LIMIT_KB)).isEqualTo(Verdict.TLE);
        assertThat(VerdictMapper.map(6, null, LIMIT_KB)).isEqualTo(Verdict.CE);
        assertThat(VerdictMapper.map(13, null, LIMIT_KB)).isEqualTo(Verdict.IE);
        assertThat(VerdictMapper.map(14, null, LIMIT_KB)).isEqualTo(Verdict.IE);
    }

    @Test
    void runtimeErrorsMapToRE_belowMemoryCap() {
        for (int id = 7; id <= 12; id++) {
            assertThat(VerdictMapper.map(id, 1000, LIMIT_KB)).isEqualTo(Verdict.RE);
        }
    }

    @Test
    void runtimeErrorAtMemoryCapBecomesMLE() {
        assertThat(VerdictMapper.map(11, LIMIT_KB, LIMIT_KB)).isEqualTo(Verdict.MLE);
        assertThat(VerdictMapper.map(11, LIMIT_KB + 5, LIMIT_KB)).isEqualTo(Verdict.MLE);
        assertThat(VerdictMapper.map(11, LIMIT_KB - 5, LIMIT_KB)).isEqualTo(Verdict.RE);
    }

    @Test
    void queuedOrProcessingNeverEscapesAsAVerdict() {
        assertThat(VerdictMapper.map(1, null, LIMIT_KB)).isEqualTo(Verdict.IE);
        assertThat(VerdictMapper.map(2, null, LIMIT_KB)).isEqualTo(Verdict.IE);
    }
}
