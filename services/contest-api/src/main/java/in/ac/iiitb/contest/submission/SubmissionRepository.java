package in.ac.iiitb.contest.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** Owner-scoped fetch — a student may only read their own submission. */
    Optional<Submission> findByIdAndUserId(Long id, Long userId);

    /** A student's own submissions, newest first (the "my submissions" panel). */
    List<Submission> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Day 9 — every judged submission in a contest, in submission-time order, for the scoreboard rebuild. */
    List<Submission> findByContestIdAndStatusOrderByCreatedAtAsc(Long contestId, String status);

}
