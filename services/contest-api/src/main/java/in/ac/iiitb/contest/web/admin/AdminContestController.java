package in.ac.iiitb.contest.web.admin;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.web.dto.ContestResponse;
import in.ac.iiitb.contest.web.dto.CreateContestRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only (the /api/admin/** role gate lives in SessionAuthFilter, so these
 * methods can assume the caller is an authenticated admin). Editing timing/state
 * lands with the admin UI in M4 — Day 4 is "build a contest from scratch via curl".
 */

@RestController
@RequestMapping("/api/admin/contests")
public class AdminContestController {
    private final ContestRepository contests;

    public AdminContestController(ContestRepository contests) {
        this.contests = contests;
    }

    @PostMapping
    public ResponseEntity<ContestResponse> create(@Valid @RequestBody CreateContestRequest req) {
        Contest c = contests.save(new Contest(req.title(), req.startsAt(), req.endsAt(), req.state()));
        ContestResponse createdContest =  new ContestResponse(c.getId(), c.getTitle(), c.getStartsAt(), c.getEndsAt(), c.getState());
        return new ResponseEntity<>(createdContest, HttpStatus.CREATED);
    }
}
