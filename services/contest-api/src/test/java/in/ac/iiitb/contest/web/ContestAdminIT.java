package in.ac.iiitb.contest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.ProblemRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Admin write side + role authorization. Proves "a contest can be built entirely
 * via curl": an admin creates a contest, a problem and its tests; a student then
 * sees them through the read API (samples only, never hidden data); and a student
 * hitting an /api/admin route is refused with 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContestAdminIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ContestRepository contests;
    @Autowired ProblemRepository problems;
    @Autowired TestCaseRepository tests;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        tests.deleteAll();
        problems.deleteAll();
        contests.deleteAll();
    }

    @Test
    void adminRoute_withoutSession_isUnauthorized() {
        ResponseEntity<String> r = post("/api/admin/contests", Map.of("title", "x"), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminRoute_asStudent_isForbidden() {
        ResponseEntity<String> r = post("/api/admin/contests",
                runningContestBody(), session("student", false));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).contains("ADMIN_ONLY");
    }

    @Test
    void admin_buildsAContestEndToEnd_studentThenSeesIt() throws Exception {
        String admin = session("admin", false);

        // 1) create a running contest
        ResponseEntity<String> contestResp = post("/api/admin/contests", runningContestBody(), admin);
        assertThat(contestResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long contestId = json.readTree(contestResp.getBody()).get("id").asLong();

        // 2) create a problem under it
        ResponseEntity<String> problemResp = post("/api/admin/problems", Map.of(
                "contestId", contestId, "label", "A", "title", "Sum",
                "statementMd", "# Sum\nRead a and b, print a + b.",
                "timeLimitMs", 1000, "memoryLimitMb", 256), admin);
        assertThat(problemResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long problemId = json.readTree(problemResp.getBody()).get("id").asLong();

        // 3) upload a visible sample + a hidden test
        ResponseEntity<String> testsResp = post("/api/admin/problems/" + problemId + "/test-cases", Map.of(
                "tests", List.of(
                        Map.of("ordinal", 1, "input", "2 3\n", "expectedOutput", "5\n", "sample", true),
                        Map.of("ordinal", 2, "input", "SECRET_IN\n", "expectedOutput", "SECRET_OUT\n", "sample", false))),
                admin);
        assertThat(testsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(testsResp.getBody()).get("added").asInt()).isEqualTo(2);

        // 4) a STUDENT now sees the problem through the read API...
        String student = session("student", false);
        ResponseEntity<String> list = get("/api/problems", student);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"label\":\"A\"");

        // ...with the statement and sample, but never the hidden test
        ResponseEntity<String> detail = get("/api/problems/" + problemId, student);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).contains("# Sum");
        assertThat(detail.getBody()).doesNotContain("SECRET_IN").doesNotContain("SECRET_OUT");
    }

    @Test
    void admin_createsFutureContest_studentReadsAreGated() throws Exception {
        String admin = session("admin", false);
        Instant now = Instant.now();
        // contest is "running" but hasn't started yet
        ResponseEntity<String> contestResp = post("/api/admin/contests", Map.of(
                "title", "Later", "startsAt", now.plus(Duration.ofHours(1)).toString(),
                "endsAt", now.plus(Duration.ofHours(4)).toString(), "state", "running"), admin);
        long contestId = json.readTree(contestResp.getBody()).get("id").asLong();
        post("/api/admin/problems", Map.of("contestId", contestId, "label", "A", "title", "Sum",
                "statementMd", "# Sum", "timeLimitMs", 1000, "memoryLimitMb", 256), admin);

        ResponseEntity<String> r = get("/api/problems", session("student", false));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).contains("CONTEST_NOT_STARTED");
    }

    @Test
    void admin_blankTitle_isRejected() {
        ResponseEntity<String> r = post("/api/admin/contests", Map.of(
                "title", "", "startsAt", Instant.now().toString(),
                "endsAt", Instant.now().plus(Duration.ofHours(1)).toString(), "state", "running"),
                session("admin", false));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("VALIDATION_ERROR");
    }

    // ---- helpers ----

    private Map<String, Object> runningContestBody() {
        Instant now = Instant.now();
        return Map.of(
                "title", "Curl Round",
                "startsAt", now.minus(Duration.ofHours(1)).toString(),
                "endsAt", now.plus(Duration.ofHours(3)).toString(),
                "state", "running");
    }

    private String session(String role, boolean mustChange) {
        String sid = "it-" + UUID.randomUUID();
        redisTemplate.opsForHash().putAll("sess:" + sid, Map.of(
                "userId", "admin".equals(role) ? "1" : "2",
                "loginId", role + "User",
                "displayName", role,
                "role", role,
                "mustChange", Boolean.toString(mustChange)));
        return "sid=" + sid;
    }

    private ResponseEntity<String> post(String path, Object body, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            h.add(HttpHeaders.COOKIE, cookie);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders h = new HttpHeaders();
        if (cookie != null) {
            h.add(HttpHeaders.COOKIE, cookie);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }
}
