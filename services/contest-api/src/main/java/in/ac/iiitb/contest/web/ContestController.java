package in.ac.iiitb.contest.web;

import java.time.Instant;
import java.util.List;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.ContestInfo;
import in.ac.iiitb.contest.web.dto.ContestPageResponse;
import in.ac.iiitb.contest.web.dto.ContestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Every contest ever run, newest first — the "Contests" tab. Any session, no admin gate. */
    @GetMapping("/contests")
    public ContestPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Contest> result = contests.findAllByOrderByStartsAtDesc(PageRequest.of(safePage, safeSize));
        List<ContestResponse> items = result.getContent().stream()
                .map(c -> new ContestResponse(c.getId(), c.getTitle(), c.getStartsAt(), c.getEndsAt(), c.getState()))
                .toList();
        return new ContestPageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
