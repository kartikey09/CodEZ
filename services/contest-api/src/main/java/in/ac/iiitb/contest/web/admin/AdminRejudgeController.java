package in.ac.iiitb.contest.web.admin;

import in.ac.iiitb.contest.submission.RejudgeService;
import in.ac.iiitb.contest.web.dto.RejudgeResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rejudge endpoints (Day 14). Everything under /api/admin/** is role-gated by SessionAuthFilter,
 * so these can assume an admin caller.
 *
 * All three return immediately with a count — judging is asynchronous. Watch progress on the
 * leaderboard (it updates itself as verdicts land) or via GET /api/admin/problems/{id}, whose
 * pending count drops to zero when the batch finishes.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminRejudgeController {

    private final RejudgeService rejudge;

    public AdminRejudgeController(RejudgeService rejudge) {
        this.rejudge = rejudge;
    }

    @PostMapping("/submissions/{id}/rejudge")
    public RejudgeResult submission(@PathVariable long id) {
        return rejudge.rejudgeSubmission(id);
    }

    @PostMapping("/problems/{id}/rejudge")
    public RejudgeResult problem(@PathVariable long id) {
        return rejudge.rejudgeProblem(id);
    }

    @PostMapping("/contests/{id}/rejudge")
    public RejudgeResult contest(@PathVariable long id) {
        return rejudge.rejudgeContest(id);
    }
}
