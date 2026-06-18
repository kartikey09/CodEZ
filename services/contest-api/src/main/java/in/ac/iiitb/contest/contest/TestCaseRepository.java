package in.ac.iiitb.contest.contest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    // Only sample tests are ever exposed to clients; hidden tests stay server-side.
    List<TestCase> findByProblemIdAndSampleTrueOrderByOrdinal(Long problemId);
}
