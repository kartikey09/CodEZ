package in.ac.iiitb.contest.submission;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** Owner-scoped fetch — a student may only read their own submission. */
    Optional<Submission> findByIdAndUserId(Long id, Long userId);

    /** A student's own submissions, newest first (the "my submissions" panel). */
    List<Submission> findByUserIdOrderByCreatedAtDesc(Long userId);
}
