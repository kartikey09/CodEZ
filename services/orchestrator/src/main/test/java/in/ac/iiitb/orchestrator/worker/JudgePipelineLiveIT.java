package in.ac.iiitb.orchestrator.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import in.ac.iiitb.orchestrator.judge0.Judge0Client;
import in.ac.iiitb.orchestrator.judge0.Judge0Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Day-7 contract test: drives JudgeService against a REAL Judge0 (no DB/Redis needed — JobRow/
 * ProblemRow/TestRow are hand-built). Proves the verdict matrix per language. Tagged "judge0" and
 * excluded by default; run with a Judge0 instance up:
 *
 *   docker compose -f deploy/judge0/docker-compose.judge0.yml up -d
 *   ./mvnw test -DexcludedGroups= -Dgroups=judge0 -DJUDGE0_URL=http://localhost:2358
 */
@Tag("judge0")
class JudgePipelineLiveIT {

    private final Judge0Client client = new Judge0Client(new Judge0Properties(
            System.getProperty("JUDGE0_URL", System.getenv().getOrDefault("JUDGE0_URL", "http://localhost:2358")),
            System.getProperty("JUDGE0_TOKEN", System.getenv().getOrDefault("JUDGE0_TOKEN", "")),
            3000, 15000, 3));

    private final WorkerProperties props = new WorkerProperties(
            "subq", "workers", "it",
            /* blockMs */            5000,
            /* batchCount */         10,
            /* pollInitialBackoff */ 150,
            /* pollMaxBackoff */     1500,
            /* pollMaxWait */        30000,
            /* compileOutputMax */   8192,
            /* inflightKeyPrefix */  "inflight:",
            /* userChannelPrefix */  "ch:user:",
            /* batchSize */          1,       // sequential judging in the live test
            /* reclaimIntervalMs */  60000,
            /* reclaimMinIdleMs */   30000,
            /* reclaimBatch */       10,
            /* maxDeliveries */      3,
            /* breakerFailureThreshold */ 5,
            /* breakerOpenMs */      30000,
            /* breakerPauseMs */     1000);

    private final JudgeService judge = new JudgeService(client, props);

    private static final ProblemRow SUM = new ProblemRow(1, 1000, 256, 1);
    private static final List<TestRow> SAMPLE = List.of(new TestRow(1, "2 3\n", "5\n"));

    private static final String C_OK =
            "#include <stdio.h>\nint main(){int a,b;scanf(\"%d %d\",&a,&b);printf(\"%d\\n\",a+b);return 0;}";
    private static final String CPP_OK =
            "#include <iostream>\nint main(){int a,b;std::cin>>a>>b;std::cout<<a+b<<\"\\n\";return 0;}";
    private static final String JAVA_OK =
            "public class Main{public static void main(String[] x){java.util.Scanner s=new java.util.Scanner(System.in);"
                    + "System.out.println(s.nextInt()+s.nextInt());}}";
    private static final String PY_OK = "a,b=map(int,input().split());print(a+b)";

    @Test
    void acceptedSolutions_perLanguage() {
        assertThat(run("c", C_OK).verdict()).isEqualTo(Verdict.AC);
        assertThat(run("cpp", CPP_OK).verdict()).isEqualTo(Verdict.AC);
        assertThat(run("java", JAVA_OK).verdict()).isEqualTo(Verdict.AC);
        assertThat(run("python", PY_OK).verdict()).isEqualTo(Verdict.AC);
    }

    @Test
    void wrongAnswer_isReportedWithFailingTest() {
        JudgeOutcome o = run("python", "a,b=map(int,input().split());print(a+b+1)");
        assertThat(o.verdict()).isEqualTo(Verdict.WA);
        assertThat(o.failedTest()).isEqualTo(1);
    }

    @Test
    void timeLimitExceeded() {
        JudgeOutcome o = run("c", "int main(){while(1){}return 0;}");
        assertThat(o.verdict()).isEqualTo(Verdict.TLE);
    }

    @Test
    void compilationError_keepsCompilerOutput() {
        JudgeOutcome o = run("c", "int main(){ this is not valid C }");
        assertThat(o.verdict()).isEqualTo(Verdict.CE);
        assertThat(o.compileOutput()).isNotBlank();
    }

    private JudgeOutcome run(String language, String source) {
        JobRow job = new JobRow(1, 7, 1, 1, language, source, "queued");
        return judge.judge(job, SUM, SAMPLE);
    }
}
