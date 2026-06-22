package in.ac.iiitb.orchestrator.judge0;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract unit tests for Judge0Client against a stubbed Judge0 (WireMock, in-process).
 * No Docker, so this runs in CI. Asserts base64 transport, status/stdout decoding,
 * retry-on-5xx, batch, and that a 4xx propagates without retry.
 */
class Judge0ClientTest {

    static WireMockServer wm;
    Judge0Client client;

    @BeforeAll
    static void startServer() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterAll
    static void stopServer() {
        wm.stop();
    }

    @BeforeEach
    void freshClient() {
        wm.resetAll();
        client = new Judge0Client(new Judge0Properties(wm.baseUrl(), "test-token", 2000, 5000, 3));
    }

    @Test
    void submit_base64EncodesSource_sendsToken_andAuthHeader() {
        wm.stubFor(post(urlPathEqualTo("/submissions"))
                .willReturn(okJson("{\"token\":\"tok-123\"}")));

        String token = client.submit(Judge0Submission.of("print(1)", 71, "in\n", "1\n"));

        assertThat(token).isEqualTo("tok-123");
        String src64 = b64("print(1)");
        String in64 = b64("in\n");
        wm.verify(postRequestedFor(urlPathEqualTo("/submissions"))
                .withQueryParam("base64_encoded", equalTo("true"))
                .withQueryParam("wait", equalTo("false"))
                .withHeader("X-Auth-Token", equalTo("test-token"))
                .withRequestBody(equalToJson(
                        "{\"source_code\":\"" + src64 + "\",\"language_id\":71,\"stdin\":\"" + in64 + "\"}",
                        true, true)));
    }

    @Test
    void get_decodesBase64Stdout_andReadsStatus() {
        String out64 = b64("3\n");
        wm.stubFor(get(urlPathEqualTo("/submissions/tok-123"))
                .willReturn(okJson("{\"token\":\"tok-123\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                        + "\"stdout\":\"" + out64 + "\",\"time\":\"0.01\",\"memory\":256}")));

        Judge0Result r = client.get("tok-123");

        assertThat(r.status().id()).isEqualTo(3);
        assertThat(r.status().description()).isEqualTo("Accepted");
        assertThat(r.stdout()).isEqualTo("3\n");
        assertThat(r.memory()).isEqualTo(256);
    }

    @Test
    void get_retriesOn503_thenSucceeds() {
        wm.stubFor(get(urlPathEqualTo("/submissions/retry"))
                .inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("ready"));
        wm.stubFor(get(urlPathEqualTo("/submissions/retry"))
                .inScenario("retry").whenScenarioStateIs("ready")
                .willReturn(okJson("{\"token\":\"retry\",\"status\":{\"id\":3,\"description\":\"Accepted\"}}")));

        Judge0Result r = client.get("retry");

        assertThat(r.status().id()).isEqualTo(3);
        wm.verify(2, getRequestedFor(urlPathEqualTo("/submissions/retry")));
    }

    @Test
    void get_doesNotRetryOn4xx() {
        wm.stubFor(get(urlPathEqualTo("/submissions/missing"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.get("missing")).isInstanceOf(RuntimeException.class);
        wm.verify(1, getRequestedFor(urlPathEqualTo("/submissions/missing")));   // exactly one attempt
    }

    @Test
    void submitBatch_returnsTokensInOrder() {
        wm.stubFor(post(urlPathEqualTo("/submissions/batch"))
                .willReturn(okJson("[{\"token\":\"a\"},{\"token\":\"b\"}]")));

        List<String> tokens = client.submitBatch(List.of(
                Judge0Submission.of("a", 71, null, null),
                Judge0Submission.of("b", 71, null, null)));

        assertThat(tokens).containsExactly("a", "b");
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
