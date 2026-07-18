package in.ac.iiitb.contest.web;

import in.ac.iiitb.contest.contest.AnnouncementRepository;
import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.web.dto.AnnouncementView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Student-facing announcements banner feed (Day 13). Returns the active notices for the
 * currently running contest, newest first. Requires a session (the filter enforces that)
 * but no admin role. When no contest is running it returns an empty list rather than a
 * 404, so the banner simply shows nothing.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementRepository announcements;
    private final ContestRepository contests;

    public AnnouncementController(AnnouncementRepository announcements, ContestRepository contests) {
        this.announcements = announcements;
        this.contests = contests;
    }

    @GetMapping
    public List<AnnouncementView> current() {
        return contests.findFirstByStateOrderByStartsAtDesc("running")
            .map(Contest::getId)
            .map(cid -> announcements.findByContestIdAndActiveTrueOrderByCreatedAtDesc(cid).stream()
                .map(AnnouncementView::of).toList())
            .orElseGet(List::of);
    }
}
