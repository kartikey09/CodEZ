package in.ac.iiitb.contest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import in.ac.iiitb.contest.broadcast.BroadcastProperties;
import in.ac.iiitb.contest.scoring.ScoringProperties;
import in.ac.iiitb.contest.submission.SubmissionProperties;

/**
 * Regression cover for a bug this service actually shipped: commit 7187b66 added {@code maxWaitMs} to
 * {@link BroadcastProperties} and the force-broadcast path that reads it, but never added
 * {@code app.broadcast.max-wait-ms} to application.yml. Constructor binding gave the absent primitive a 0,
 * so the standings debounce computed a zero delay and every verdict forced a full board rebuild — the
 * precise burst the throttle was written to prevent, with nothing in the logs to say so.
 *
 * {@link #shippedApplicationYmlSatisfiesEveryConstraint()} is the test that would have caught it, and now
 * fails the build if any validated key goes missing from the yml again.
 */
class ConfigPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BoundProperties.class);

    @Configuration
    @EnableConfigurationProperties({
            BroadcastProperties.class, ScoringProperties.class, SubmissionProperties.class})
    static class BoundProperties {
    }

    @Test
    void shippedApplicationYmlSatisfiesEveryConstraint() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(BroadcastProperties.class).maxWaitMs()).isEqualTo(5000);
                    assertThat(ctx.getBean(ScoringProperties.class).countCompileErrors()).isTrue();
                    assertThat(ctx.getBean(SubmissionProperties.class).allowedLanguages())
                            .contains("cpp", "java", "python");
                });
    }

    @Test
    void completeConfigBinds() {
        runner.withPropertyValues(asPropertyValues(appProps()))
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    /** The original defect: the key simply absent. It must now stop the service instead of binding 0. */
    @Test
    void missingMaxWaitMsAbortsStartup() {
        Map<String, String> incomplete = appProps();
        incomplete.remove("app.broadcast.max-wait-ms");

        runner.withPropertyValues(asPropertyValues(incomplete))
                .run(ctx -> assertThatFailureMentions(ctx, "maxWaitMs"));
    }

    /** Boxed on purpose: as a primitive boolean, an absent key would silently mean "don't penalize CEs". */
    @Test
    void missingCountCompileErrorsAbortsStartup() {
        Map<String, String> incomplete = appProps();
        incomplete.remove("app.scoring.count-compile-errors");

        runner.withPropertyValues(asPropertyValues(incomplete))
                .run(ctx -> assertThatFailureMentions(ctx, "countCompileErrors"));
    }

    @Test
    void emptyAllowedLanguagesAbortsStartup() {
        Map<String, String> bad = appProps();
        bad.put("app.submission.allowed-languages", "");   // would reject every submission

        runner.withPropertyValues(asPropertyValues(bad))
                .run(ctx -> assertThatFailureMentions(ctx, "allowedLanguages"));
    }

    // ---------- helpers ----------

    private static void assertThatFailureMentions(AssertableApplicationContext ctx, String field) {
        assertThat(ctx).hasFailed();
        assertThat(ctx.getStartupFailure())
                .isInstanceOf(ConfigurationPropertiesBindException.class)
                .hasStackTraceContaining(field);
    }

    private static String[] asPropertyValues(Map<String, String> props) {
        return props.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private static Map<String, String> appProps() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("app.broadcast.verdict-channel-pattern", "ch:user:*");
        p.put("app.broadcast.standings-channel-prefix", "ch:standings:");
        p.put("app.broadcast.debounce-ms", "500");
        p.put("app.broadcast.max-wait-ms", "5000");
        p.put("app.broadcast.broadcast-limit", "500");
        p.put("app.scoring.key-prefix", "standings:");
        p.put("app.scoring.penalty-per-wrong", "20");
        p.put("app.scoring.cache-ttl-ms", "5000");
        p.put("app.scoring.count-compile-errors", "true");
        p.put("app.submission.allowed-languages", "c,cpp,java,python");
        p.put("app.submission.max-source-bytes", "65536");
        p.put("app.submission.cooldown-ms", "10000");
        p.put("app.submission.inflight-ttl-seconds", "120");
        p.put("app.submission.grace-seconds", "5");
        p.put("app.submission.stream-key", "subq");
        return p;
    }
}
