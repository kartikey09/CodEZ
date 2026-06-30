package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class LanguageRuntimeTest {

    @Test
    void slugsResolveToJudge0LanguageIds() {
        assertThat(LanguageRuntime.fromSlug("c").judge0Id()).isEqualTo(50);
        assertThat(LanguageRuntime.fromSlug("cpp").judge0Id()).isEqualTo(54);
        assertThat(LanguageRuntime.fromSlug("java").judge0Id()).isEqualTo(62);
        assertThat(LanguageRuntime.fromSlug("python").judge0Id()).isEqualTo(71);
    }

    @Test
    void unknownSlugIsRejected() {
        assertThatThrownBy(() -> LanguageRuntime.fromSlug("rust"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cpuMultipliersApply() {
        int base = 1000;   // 1 s problem limit
        assertThat(LanguageRuntime.C.cpuLimitSeconds(base)).isCloseTo(1.0, within(1e-9));      // x1
        assertThat(LanguageRuntime.CPP.cpuLimitSeconds(base)).isCloseTo(1.0, within(1e-9));    // x1
        assertThat(LanguageRuntime.JAVA.cpuLimitSeconds(base)).isCloseTo(3.0, within(1e-9));   // x2 + 1s
        assertThat(LanguageRuntime.PYTHON.cpuLimitSeconds(base)).isCloseTo(3.0, within(1e-9)); // x3
    }

    @Test
    void wallHasHeadroomOverCpu_andMemoryConvertsToKb() {
        assertThat(LanguageRuntime.C.wallLimitSeconds(1000)).isCloseTo(3.0, within(1e-9));     // 1*2 + 1
        assertThat(LanguageRuntime.JAVA.wallLimitSeconds(1000)).isCloseTo(7.0, within(1e-9));  // 3*2 + 1
        assertThat(LanguageRuntime.C.memoryLimitKb(256)).isEqualTo(262144);
    }
}
