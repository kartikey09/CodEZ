package in.ac.iiitb.contest.web.admin;

import in.ac.iiitb.contest.contest.Announcement;
import in.ac.iiitb.contest.contest.AnnouncementRepository;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.error.NoContestFoundException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.AnnouncementView;
import in.ac.iiitb.contest.web.dto.CreateAnnouncementRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin announcement management (Day 13). /api/admin/** is role-gated by the session
 * filter, so these methods assume an admin caller. Posting sends a notice to a contest;
 * students pick it up from the (public-to-authenticated) GET /api/announcements banner
 * endpoint. Retracting is a soft delete (active = false).
 */
@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementRepository announcements;
    private final ContestRepository contests;

    public AdminAnnouncementController(AnnouncementRepository announcements, ContestRepository contests) {
        this.announcements = announcements;
        this.contests = contests;
    }

    @GetMapping
    public List<AnnouncementView> list(@RequestParam long contestId) {
        return announcements.findByContestIdOrderByCreatedAtDesc(contestId).stream()
            .map(AnnouncementView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementView create(@Valid @RequestBody CreateAnnouncementRequest req) {
        // A notice must attach to a real contest.
        contests.findById(req.contestId()).orElseThrow(NoContestFoundException::new);
        Announcement a = announcements.save(new Announcement(req.contestId(), req.message().trim()));
        return AnnouncementView.of(a);
    }

    @PostMapping("/{id}/deactivate")
    public AnnouncementView deactivate(@PathVariable long id) {
        Announcement a = announcements.findById(id).orElseThrow(NotFoundException::new);
        a.setActive(false);
        announcements.save(a);
        return AnnouncementView.of(a);
    }
}
