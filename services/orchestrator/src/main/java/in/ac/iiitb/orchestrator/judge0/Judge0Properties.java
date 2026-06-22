package in.ac.iiitb.orchestrator.judge0;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.judge0")
public record Judge0Properties(
        String baseUrl,
        String authToken,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxRetries) {
}
