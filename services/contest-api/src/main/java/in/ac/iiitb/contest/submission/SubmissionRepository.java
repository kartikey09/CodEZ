package in.ac.iiitb.contest.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** Owner-scoped fetch — a student may only read their own submission. */
    Optional<Submission> findByIdAndUserId(Long id, Long userId);

    /** A student's own graded submissions, newest first (the "my submissions" panel) — Run rows excluded. */
    List<Submission> findByUserIdAndKindOrderByCreatedAtDesc(Long userId, String kind);

    /** Day 9 — every judged, graded submission in a contest, in submission-time order, for the scoreboard
     * rebuild. Kind-scoped so practice Run rows never affect standings. */
    List<Submission> findByContestIdAndStatusAndKindOrderByCreatedAtAsc(Long contestId, String status, String kind);

}
