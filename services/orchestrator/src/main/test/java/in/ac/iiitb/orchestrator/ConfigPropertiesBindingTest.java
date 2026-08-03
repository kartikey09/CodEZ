package in.ac.iiitb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;

import in.ac.iiitb.orchestrator.judge0.Judge0Properties;
import in.ac.iiitb.orchestrator.worker.WorkerProperties;

/**
 * Constructor binding fills an absent property with the primitive default — 0 for a number, false for a
 * boolean — so a forgotten or misspelled key produces a silently mis-configured service rather than a
 * failure. This project has already shipped that bug once (app.broadcast.max-wait-ms went missing, which
 * zeroed the standings debounce and made every verdict force a full board rebuild).
 *
 * These tests pin both halves of the fix:
 *   1. the application.yml we actually ship satisfies every constraint — the check that would have caught
 *      the original bug, and that fails the moment someone adds a validated field without a default;
 *   2. dropping a single key really does abort startup, for a number AND for a boolean (booleans only
 *      behave this way because the record boxes them — a primitive cannot tell "absent" from "false").
 */
class ConfigPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BoundProperties.class);

    @Configuration
    @EnableConfigurationProperties({WorkerProperties.class, Judge0Properties.class})
    static class BoundProperties {
    }

    @Test
    void shippedApplicationYmlSatisfiesEveryConstraint() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    WorkerProperties worker = ctx.getBean(WorkerProperties.class);
                    // spot-check the two P0 knobs whose absence is invisible at runtime
                    assertThat(worker.inflightTtlSeconds()).isEqualTo(120);
                    assertThat(worker.submitEarlyExit()).isTrue();
                    assertThat(ctx.getBean(Judge0Properties.class).readTimeoutMs()).isPositive();
                });
    }

    @Test
    void completeConfigBinds() {
        runner.withPropertyValues(asPropertyValues(workerProps()))
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void missingNumericKeyAbortsStartup() {
        Map<String, String> incomplete = workerProps();
        incomplete.remove("app.worker.inflight-ttl-seconds");

        runner.withPropertyValues(asPropertyValues(incomplete))
                .run(ctx -> assertThatFailureMentions(ctx, "inflightTtlSeconds"));
    }

    /** The boolean case only fails because WorkerProperties boxes it; as a primitive this would bind false. */
    @Test
    void missingBooleanKeyAbortsStartup() {
        Map<String, String> incomplete = workerProps();
        incomplete.remove("app.worker.submit-early-exit");

        runner.withPropertyValues(asPropertyValues(incomplete))
                .run(ctx -> assertThatFailureMentions(ctx, "submitEarlyExit"));
    }

    @Test
    void nonsensicalValueAbortsStartup() {
        Map<String, String> bad = workerProps();
        bad.put("app.worker.judge-concurrency", "0");   // a pool that can never run anything

        runner.withPropertyValues(asPropertyValues(bad))
                .run(ctx -> assertThatFailureMentions(ctx, "judgeConcurrency"));
    }

    // ---------- helpers ----------

    /**
     * The top-level ConfigurationPropertiesBindException only names the record; the offending field is
     * reported by the nested BindValidationException, which is what an operator actually reads in the
     * startup log — so assert against the whole chain.
     */
    private static void assertThatFailureMentions(AssertableApplicationContext ctx, String field) {
        assertThat(ctx).hasFailed();
        assertThat(ctx.getStartupFailure())
                .isInstanceOf(ConfigurationPropertiesBindException.class)
                .hasStackTraceContaining(field);   // the operator is told exactly which knob is wrong
    }

    private static String[] asPropertyValues(Map<String, String> props) {
        return props.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    /** Every app.worker.* key, so a test can prove the effect of removing exactly one. */
    private static Map<String, String> workerProps() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("app.worker.stream-key", "subq");
        p.put("app.worker.group", "workers");
        p.put("app.worker.consumer", "orchestrator-1");
        p.put("app.worker.block-ms", "5000");
        p.put("app.worker.batch-count", "10");
        p.put("app.worker.poll-initial-backoff-ms", "150");
        p.put("app.worker.poll-max-backoff-ms", "1500");
        p.put("app.worker.poll-max-wait-ms", "30000");
        p.put("app.worker.compile-output-max-bytes", "8192");
        p.put("app.worker.inflight-key-prefix", "inflight:");
        p.put("app.worker.user-channel-prefix", "ch:user:");
        p.put("app.worker.batch-size", "20");
        p.put("app.worker.reclaim-interval-ms", "30000");
        p.put("app.worker.reclaim-min-idle-ms", "90000");
        p.put("app.worker.reclaim-batch", "50");
        p.put("app.worker.max-deliveries", "3");
        p.put("app.worker.breaker-failure-threshold", "5");
        p.put("app.worker.breaker-open-ms", "15000");
        p.put("app.worker.breaker-pause-ms", "1000");
        p.put("app.worker.judge-concurrency", "16");
        p.put("app.worker.drain-timeout-ms", "60000");
        p.put("app.worker.test-cache-max-entries", "500");
        p.put("app.worker.test-cache-expire-after-access-ms", "3600000");
        p.put("app.worker.submit-early-exit", "true");
        p.put("app.worker.inflight-ttl-seconds", "120");
        // app.judge0.* too, since the runner binds both records
        p.put("app.judge0.base-url", "http://localhost:2358");
        p.put("app.judge0.auth-token", "");
        p.put("app.judge0.connect-timeout-ms", "2000");
        p.put("app.judge0.read-timeout-ms", "10000");
        p.put("app.judge0.max-retries", "3");
        return p;
    }
}
