package in.ac.iiitb.contest.contest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    List<Problem> findByContestIdOrderByLabel(Long contestId);
}
