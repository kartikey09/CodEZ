package in.ac.iiitb.orchestrator.judge0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The Day-6 contract spike, in code: the curl matrix against a REAL Judge0.
 * Tagged "judge0" and excluded from normal builds (it needs the Judge0 CE stack up).
 * Run it explicitly:
 *
 *   docker compose -f deploy/judge0/docker-compose.judge0.yml up -d
 *   ./mvnw test -DexcludedGroups= -Dgroups=judge0 \
 *       -DJUDGE0_URL=http://localhost:2358 -DJUDGE0_TOKEN=...   (token only if set in judge0.conf)
 *
 * Language IDs below are for Judge0 CE 1.13.x — confirm against your instance with
 *   curl -s "$JUDGE0_URL/languages" | jq '.[] | {id, name}'
 */
@Tag("judge0")
class Judge0LiveIT {

    static final int C = 50;        // GCC

    private final Judge0Client client = new Judge0Client(new Judge0Properties(
            System.getProperty("JUDGE0_URL", System.getenv().getOrDefault("JUDGE0_URL", "http://localhost:2358")),
            System.getProperty("JUDGE0_TOKEN", System.getenv().getOrDefault("JUDGE0_TOKEN", "")),
            3000, 15000, 3));

    private static final String SUM_C =
            "#include <stdio.h>\nint main(){int a,b;scanf(\"%d %d\",&a,&b);printf(\"%d\\n\",a+b);return 0;}";

    @Test
    void acceptedProgram_reachesStatus3() {
        Judge0Result r = runToCompletion(Judge0Submission.of(SUM_C, C, "2 3\n", "5\n"));
        assertThat(r.status().id()).isEqualTo(3);   // Accepted
    }

    @Test
    void wrongAnswer_reachesStatus4() {
        Judge0Result r = runToCompletion(Judge0Submission.of(SUM_C, C, "2 3\n", "6\n"));
        assertThat(r.status().id()).isEqualTo(4);   // Wrong Answer
    }

    @Test
    void compilationError_reachesStatus6() {
        Judge0Result r = runToCompletion(Judge0Submission.of("int main(){ this is not C }", C, "", ""));
        assertThat(r.status().id()).isEqualTo(6);   // Compilation Error
        assertThat(r.compileOutput()).isNotBlank();
    }

    @Test
    void timeLimitExceeded_reachesStatus5() {
        String loop = "int main(){ while(1){} return 0; }";
        // tight cpu limit so the infinite loop trips TLE quickly
        Judge0Result r = runToCompletion(new Judge0Submission(loop, C, null, null, 1.0, 2.0, 128000));
        assertThat(r.status().id()).isEqualTo(5);   // Time Limit Exceeded
    }

    /** Submit, then poll the token until Judge0 leaves the In-Queue/Processing states (id >= 3). */
    private Judge0Result runToCompletion(Judge0Submission submission) {
        String token = client.submit(submission);
        for (int i = 0; i < 60; i++) {
            Judge0Result r = client.get(token);
            if (r.status() != null && r.status().id() != null && r.status().id() >= 3) {
                return r;
            }
            sleep(300);
        }
        throw new AssertionError("Judge0 did not finish within the polling window");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
