package in.ac.iiitb.contest.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    // Only sample tests are ever exposed to clients; hidden tests stay server-side.
    List<TestCase> findByProblemIdAndSampleTrueOrderByOrdinal(Long problemId);

    // ----- Day 14: the authoring UI needs to enumerate and remove test cases -----

    /** Every test for a problem, in ordinal order. The admin view maps these to METADATA only
     *  (ordinal, sample flag, sizes) for hidden tests — see AdminProblemController. */
    List<TestCase> findByProblemIdOrderByOrdinal(Long problemId);

    Optional<TestCase> findByProblemIdAndOrdinal(Long problemId, int ordinal);

    long countByProblemId(Long problemId);
}
