package in.ac.iiitb.contest.submission;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admission-control knobs (bound from app.submission in application.yml).
 *   allowedLanguages  - the only language slugs accepted
 *   maxSourceBytes    - hard cap on UTF-8 source size (64 KB)
 *   cooldownMs         - minimum gap between a user's submissions
 *   inflightTtlSeconds - safety TTL on the "one submission judging at a time" lock
 *                        (the orchestrator clears it on verdict; the TTL guards against a dead worker)
 *   graceSeconds       - leeway past ends_at so an in-flight click at the buzzer still lands
 *   streamKey          - the Redis stream the orchestrator consumes
 */
@ConfigurationProperties(prefix = "app.submission")
public record SubmissionProperties(
        List<String> allowedLanguages,
        int maxSourceBytes,
        long cooldownMs,
        long inflightTtlSeconds,
        long graceSeconds,
        String streamKey) {
}
