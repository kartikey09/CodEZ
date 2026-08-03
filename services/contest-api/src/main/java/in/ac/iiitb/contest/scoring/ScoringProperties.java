package in.ac.iiitb.contest.scoring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds app.scoring.*. {@code keyPrefix} namespaces the per-contest Redis keys; {@code cacheTtlMs} bounds
 * how often the board is recomputed from the submissions table; the rest feed the ICPC rules.
 *
 * {@code countCompileErrors} is boxed so an absent key fails validation: as a primitive it would bind to
 * false, quietly flipping the contest to non-classic ICPC penalty rules with nothing to notice it.
 */
@Validated
@ConfigurationProperties(prefix = "app.scoring")
public record ScoringProperties(
        @NotBlank String keyPrefix,
        /** 0 is legitimate: a contest run with no penalty for wrong attempts. */
        @PositiveOrZero int penaltyPerWrong,
        /** 0 is legitimate: never serve a cached board, always recompute. */
        @PositiveOrZero long cacheTtlMs,
        @NotNull Boolean countCompileErrors) {

    public ScoringRules rules() {
        return new ScoringRules(penaltyPerWrong, countCompileErrors);
    }
}
