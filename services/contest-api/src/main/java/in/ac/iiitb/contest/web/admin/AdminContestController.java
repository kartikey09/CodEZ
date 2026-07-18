package in.ac.iiitb.contest.web.admin;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.error.InvalidContestUpdateException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.ContestResponse;
import in.ac.iiitb.contest.web.dto.CreateContestRequest;
import in.ac.iiitb.contest.web.dto.UpdateContestRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Admin-only (the /api/admin/** role gate lives in SessionAuthFilter, so these
 * methods can assume the caller is an authenticated admin). Day 4 created contests via
 * curl; Day 13 adds the list + timing/state edits the admin UI drives.
 */
@RestController
@RequestMapping("/api/admin/contests")
public class AdminContestController {

    /** The states a contest may hold — must match V1's CHECK-comment values. */
    private static final Set<String> STATES = Set.of("draft", "published", "running", "finished");

    private final ContestRepository contests;

    public AdminContestController(ContestRepository contests) {
        this.contests = contests;
    }

    @GetMapping
    public List<ContestResponse> list() {
        return contests.findAll().stream()
            .map(c -> new ContestResponse(c.getId(), c.getTitle(), c.getStartsAt(), c.getEndsAt(), c.getState()))
            .toList();
    }

    @PostMapping
    public ResponseEntity<ContestResponse> create(@Valid @RequestBody CreateContestRequest req) {
        Contest c = contests.save(new Contest(req.title(), req.startsAt(), req.endsAt(), req.state()));
        ContestResponse createdContest = new ContestResponse(c.getId(), c.getTitle(), c.getStartsAt(), c.getEndsAt(), c.getState());
        return new ResponseEntity<>(createdContest, HttpStatus.CREATED);
    }

    /**
     * Partial update of timing/state. Only non-null fields are applied. Validates the
     * new state against the allowed set and that the (possibly new) window has end after
     * start, so the admin can't accidentally save a contest that can never run.
     */
    @PatchMapping("/{id}")
    public ContestResponse update(@PathVariable long id, @RequestBody UpdateContestRequest req) {
        Contest c = contests.findById(id).orElseThrow(NotFoundException::new);

        if (req.state() != null) {
            String state = req.state().trim().toLowerCase();
            if (!STATES.contains(state)) {
                throw new InvalidContestUpdateException(
                    "state must be one of draft, published, running, finished");
            }
            c.setState(state);
        }
        if (req.startsAt() != null) {
            c.setStartsAt(req.startsAt());
        }
        if (req.endsAt() != null) {
            c.setEndsAt(req.endsAt());
        }

        Instant starts = c.getStartsAt();
        Instant ends = c.getEndsAt();
        if (starts != null && ends != null && !ends.isAfter(starts)) {
            throw new InvalidContestUpdateException("endsAt must be after startsAt");
        }

        contests.save(c);
        return new ContestResponse(c.getId(), c.getTitle(), c.getStartsAt(), c.getEndsAt(), c.getState());
    }
}
