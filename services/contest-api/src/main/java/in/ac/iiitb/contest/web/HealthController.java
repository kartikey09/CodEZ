package in.ac.iiitb.contest.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Day-1 liveness endpoint. Real domain endpoints (problems, submissions,
 * leaderboard) arrive on later days. Kept dependency-free so the context
 * loads in CI without a database.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "contest-api",
                "status", "ok",
                "time", Instant.now().toString()
        );
    }
}
