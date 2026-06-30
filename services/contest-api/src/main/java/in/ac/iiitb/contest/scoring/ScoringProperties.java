package in.ac.iiitb.contest.scoring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.scoring.*. {@code keyPrefix} namespaces the per-contest Redis keys; {@code cacheTtlMs} bounds
 * how often the board is recomputed from the submissions table; the rest feed the ICPC rules.
 */
@ConfigurationProperties(prefix = "app.scoring")
public record ScoringProperties(
        String keyPrefix,
        int penaltyPerWrong,
        long cacheTtlMs,
        boolean countCompileErrors) {

    public ScoringRules rules() {
        return new ScoringRules(penaltyPerWrong, countCompileErrors);
    }
}
