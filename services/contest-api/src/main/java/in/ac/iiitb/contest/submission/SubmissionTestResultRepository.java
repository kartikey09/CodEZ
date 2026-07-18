package in.ac.iiitb.contest.submission;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, Long> {
    List<SubmissionTestResult> findBySubmissionIdOrderByOrdinal(Long submissionId);
}
