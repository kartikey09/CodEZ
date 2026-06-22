package in.ac.iiitb.orchestrator.judge0;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Thin, synchronous Judge0 CE client. Responsibilities kept deliberately small:
 *   - base64 transport (encode source/stdin/expected; decode stdout/stderr/compile_output),
 *   - connect + read timeouts,
 *   - bounded retries with linear backoff on transport errors (IO/timeout) and 5xx only —
 *     a 4xx is a contract bug and propagates immediately,
 *   - the X-Auth-Token header when a token is configured.
 *
 * It does NOT poll-until-done or map verdicts; callers (the Day-7 worker, the live IT) decide that.
 */
@Component
public class Judge0Client {

    private static final String FIELDS = "token,status,stdout,stderr,compile_output,time,memory";

    private final RestClient http;
    private final int maxRetries;

    public Judge0Client(Judge0Properties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings));
        if (props.authToken() != null && !props.authToken().isBlank()) {
            builder.defaultHeader("X-Auth-Token", props.authToken());
        }
        this.http = builder.build();
        this.maxRetries = Math.max(1, props.maxRetries());
    }

    /** Enqueue a single submission; returns its token. */
    public String submit(Judge0Submission submission) {
        Judge0TokenResponse res = withRetry(() -> http.post()
                .uri(uri -> uri.path("/submissions")
                        .queryParam("base64_encoded", "true")
                        .queryParam("wait", "false")
                        .build())
                .body(toWire(submission))
                .retrieve()
                .body(Judge0TokenResponse.class));
        return res.token();
    }

    /** Enqueue many submissions in one call; returns tokens in the same order. */
    public List<String> submitBatch(List<Judge0Submission> batch) {
        List<Map<String, Object>> wire = batch.stream().map(this::toWire).toList();
        Judge0TokenResponse[] res = withRetry(() -> http.post()
                .uri(uri -> uri.path("/submissions/batch")
                        .queryParam("base64_encoded", "true")
                        .build())
                .body(Map.of("submissions", wire))
                .retrieve()
                .body(Judge0TokenResponse[].class));
        return Arrays.stream(res).map(Judge0TokenResponse::token).toList();
    }

    /** Fetch one result by token (decoded). */
    public Judge0Result get(String token) {
        Judge0Result raw = withRetry(() -> http.get()
                .uri(uri -> uri.path("/submissions/{token}")
                        .queryParam("base64_encoded", "true")
                        .queryParam("fields", FIELDS)
                        .build(token))
                .retrieve()
                .body(Judge0Result.class));
        return decode(raw);
    }

    /** Fetch many results by token (decoded), preserving order. */
    public List<Judge0Result> getBatch(List<String> tokens) {
        String joined = String.join(",", tokens);
        Judge0Batch batch = withRetry(() -> http.get()
                .uri(uri -> uri.path("/submissions/batch")
                        .queryParam("tokens", joined)
                        .queryParam("base64_encoded", "true")
                        .queryParam("fields", FIELDS)
                        .build())
                .retrieve()
                .body(Judge0Batch.class));
        List<Judge0Result> out = new ArrayList<>();
        if (batch != null && batch.submissions() != null) {
            for (Judge0Result r : batch.submissions()) {
                out.add(decode(r));
            }
        }
        return out;
    }

    // ---- wire helpers ----

    private Map<String, Object> toWire(Judge0Submission s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_code", b64(s.sourceCode()));
        m.put("language_id", s.languageId());
        if (s.stdin() != null) {
            m.put("stdin", b64(s.stdin()));
        }
        if (s.expectedOutput() != null) {
            m.put("expected_output", b64(s.expectedOutput()));
        }
        if (s.cpuTimeLimit() != null) {
            m.put("cpu_time_limit", s.cpuTimeLimit());
        }
        if (s.wallTimeLimit() != null) {
            m.put("wall_time_limit", s.wallTimeLimit());
        }
        if (s.memoryLimit() != null) {
            m.put("memory_limit", s.memoryLimit());
        }
        return m;
    }

    private Judge0Result decode(Judge0Result r) {
        if (r == null) {
            return null;
        }
        return new Judge0Result(r.token(), r.status(),
                unb64(r.stdout()), unb64(r.stderr()), unb64(r.compileOutput()), r.time(), r.memory());
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    // ---- retry ----

    private <T> T withRetry(Supplier<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return call.get();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                last = e;                       // 5xx or IO/timeout — transient, retry
                backoff(attempt);
            }
        }
        throw new Judge0Exception("Judge0 call failed after " + maxRetries + " attempt(s)", last);
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new Judge0Exception("Interrupted while backing off", ie);
        }
    }
}
