package in.ac.iiitb.contest.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** Active notices for the banner, newest first. */
    List<Announcement> findByContestIdAndActiveTrueOrderByCreatedAtDesc(long contestId);

    /** Everything for a contest (admin view), newest first. */
    List<Announcement> findByContestIdOrderByCreatedAtDesc(long contestId);
}
