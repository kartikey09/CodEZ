package in.ac.iiitb.contest.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    // ----- Day 14: rejudge -----

    /**
     * Ids of the already-judged, graded attempts for one problem — the rejudge target set.
     * Scoped to status='done' (rows still queued/running will be judged anyway, and re-enqueueing
     * them would race the in-flight worker) and to kind='submit' (practice Run rows never affect
     * standings, and re-running one would collide with submission_test_results' unique key).
     */
    @Query("SELECT s.id FROM Submission s WHERE s.problemId = :problemId "
        + "AND s.status = 'done' AND s.kind = 'submit' ORDER BY s.id")
    List<Long> findRejudgeTargetsByProblem(@Param("problemId") long problemId);

    /** Same target rule, scoped to a whole contest. */
    @Query("SELECT s.id FROM Submission s WHERE s.contestId = :contestId "
        + "AND s.status = 'done' AND s.kind = 'submit' ORDER BY s.id")
    List<Long> findRejudgeTargetsByContest(@Param("contestId") long contestId);

    /**
     * Return the given rows to the queue. Clearing the verdict columns is not cosmetic: the worker's
     * writeback carries "WHERE status <> 'done'" and it skips any job whose row already reads done,
     * so without this reset a re-enqueued record would be acked without ever being re-judged.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Submission s SET s.status = 'queued', s.verdict = null, s.failedTest = null, "
        + "s.passedTests = null, s.totalTests = null, s.execTimeMs = null, s.memoryKb = null, "
        + "s.judgedAt = null WHERE s.id IN :ids")
    int resetForRejudge(@Param("ids") List<Long> ids);

    /** Graded attempts at a problem that aren't finished yet — i.e. rejudge still in progress. */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.problemId = :problemId "
        + "AND s.kind = 'submit' AND s.status <> 'done'")
    long countPendingByProblem(@Param("problemId") long problemId);
}
