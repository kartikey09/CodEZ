package in.ac.iiitb.contest.scoring;

import in.ac.iiitb.contest.session.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live leaderboard, readable by any authenticated user (the SessionAuthFilter already guards every
 * path). {@code /me} returns just the caller's row and rank.
 */
@RestController
@RequestMapping("/api/contests")
public class StandingsController {

    private final ScoreboardService scoreboard;

    public StandingsController(ScoreboardService scoreboard) {
        this.scoreboard = scoreboard;
    }

    @GetMapping("/{contestId}/standings")
    public StandingsResponse standings(@PathVariable long contestId,
                                       @RequestParam(defaultValue = "200") int limit) {
        return scoreboard.standings(contestId, Math.max(1, Math.min(limit, 1000)));
    }

    @GetMapping("/{contestId}/standings/me")
    public MyStanding myStanding(@PathVariable long contestId, HttpServletRequest http) {
        long userId = AuthContext.user(http).userId();
        return scoreboard.myStanding(contestId, userId);
    }
}
