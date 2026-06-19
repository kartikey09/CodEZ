package in.ac.iiitb.contest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.contest.TestCase;
import in.ac.iiitb.contest.contest.TestCaseRepository;
import in.ac.iiitb.contest.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Read-side end-to-end against real Postgres + Redis. Sessions are planted directly
 * into Redis to simulate what auth-service does on login — proving contest-api
 * validates the SAME session record without auth-service running, and that hidden
 * test data never leaks into any response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContestReadIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ContestRepository contests;
    @Autowired ProblemRepository problems;
    @Autowired TestCaseRepository tests;
    @Autowired StringRedisTemplate redisTemplate;

    Long contestId;
    Long problemAId;

    @BeforeEach
    void seed() {
        tests.deleteAll();
        problems.deleteAll();
        contests.deleteAll();

        Instant now = Instant.now();
        Contest c = contests.save(new Contest("IT Round",
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)), "running"));
        contestId = c.getId();

        Problem a = problems.save(new Problem(c.getId(), "A", "Sum",
                "# Sum\nRead a and b, print a + b.", 1000, 256));
        problemAId = a.getId();
        tests.save(new TestCase(a.getId(), 1, "1 2\n", "3\n", true));                       // sample
        tests.save(new TestCase(a.getId(), 2, "HIDDEN_INPUT_42\n", "HIDDEN_OUTPUT_99\n", false)); // hidden

        Problem b = problems.save(new Problem(c.getId(), "B", "Echo", "# Echo\nPrint input.", 1000, 256));
        tests.save(new TestCase(b.getId(), 1, "hi\n", "hi\n", true));
    }

    @Test
    void problems_withoutSession_isUnauthorized() {
        assertThat(get("/api/problems", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void problems_withMustChangeSession_isForbidden() {
        ResponseEntity<String> r = get("/api/problems", session("student", true));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).contains("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void problems_withValidSession_listsWithoutHiddenData() {
        ResponseEntity<String> r = get("/api/problems", session("student", false));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"label\":\"A\"").contains("\"label\":\"B\"");
        assertThat(r.getBody()).doesNotContain("statementMd").doesNotContain("HIDDEN_INPUT_42");
    }

    @Test
    void problemDetail_exposesSamplesButNeverHiddenTests() {
        ResponseEntity<String> r = get("/api/problems/" + problemAId, session("student", false));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("# Sum");
        assertThat(r.getBody()).contains("samples");
        assertThat(r.getBody()).doesNotContain("HIDDEN_INPUT_42");
        assertThat(r.getBody()).doesNotContain("HIDDEN_OUTPUT_99");
    }

    @Test
    void time_requiresSession() {
        assertThat(get("/api/time", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/time", session("student", false)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void beforeStart_problemsAreGated() {
        Contest c = contests.findById(contestId).orElseThrow();
        c.setStartsAt(Instant.now().plus(Duration.ofHours(1)));
        contests.save(c);

        ResponseEntity<String> r = get("/api/problems", session("student", false));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).contains("CONTEST_NOT_STARTED");
    }

    private String session(String role, boolean mustChange) {
        String sid = "it-" + UUID.randomUUID();
        redisTemplate.opsForHash().putAll("sess:" + sid, Map.of(
                "userId", "7", "loginId", "stud007", "displayName", "Student Seven",
                "role", role, "mustChange", Boolean.toString(mustChange)));
        return "sid=" + sid;
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders h = new HttpHeaders();
        if (cookie != null) {
            h.add(HttpHeaders.COOKIE, cookie);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }
}
