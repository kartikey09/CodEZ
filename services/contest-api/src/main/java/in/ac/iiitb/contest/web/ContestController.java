package in.ac.iiitb.contest.web;

import java.time.Instant;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.ContestInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the running contest. Deliberately singular (/api/contest) — "the contest that is on
 * right now" — distinct from /api/contests/{id}/... which addresses a specific board.
 *
 * Unlike /api/problems this does NOT gate on startsAt: a logged-in student in the lobby needs the window to
 * render a countdown to the start. Problem content stays protected by the existing gate. Session required
 * (the filter enforces it for any non-public path); 404 when no contest is in the running state.
 */
@RestController
@RequestMapping("/api")
public class ContestController {

    private final ContestRepository contests;

    public ContestController(ContestRepository contests) {
        this.contests = contests;
    }

    @GetMapping("/contest")
    public ContestInfo current() {
        Contest c = contests.findFirstByStateOrderByStartsAtDesc("running").orElseThrow(NotFoundException::new);
        return new ContestInfo(c.getId(), c.getTitle(), c.getStartsAt(), c.getEndsAt(), c.getState(),
                Instant.now().toEpochMilli());
    }
}
