package in.ac.iiitb.contest.web.dto;

import in.ac.iiitb.contest.contest.Announcement;

import java.time.Instant;

/** Announcement as returned to admin and student clients. */
public record AnnouncementView(
        long id,
        long contestId,
        String message,
        boolean active,
        Instant createdAt) {

    public static AnnouncementView of(Announcement a) {
        return new AnnouncementView(a.getId(), a.getContestId(), a.getMessage(),
                a.isActive(), a.getCreatedAt());
    }
}
