package in.ac.iiitb.contest.scoring;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Force a full recompute of a contest's board. Lives under /api/admin/** so the SessionAuthFilter's admin
 * gate applies — only an authenticated admin can call it. Rarely needed (the board self-rebuilds from the
 * table), but handy after a bulk data fix or a rejudge.
 */
@RestController
@RequestMapping("/api/admin/contests")
public class AdminStandingsController {

    private final ScoreboardService scoreboard;

    public AdminStandingsController(ScoreboardService scoreboard) {
        this.scoreboard = scoreboard;
    }

    @PostMapping("/{contestId}/standings/rebuild")
    public Map<String, Object> rebuild(@PathVariable long contestId) {
        int users = scoreboard.rebuild(contestId);
        return Map.of("rebuilt", true, "users", users);
    }
}
