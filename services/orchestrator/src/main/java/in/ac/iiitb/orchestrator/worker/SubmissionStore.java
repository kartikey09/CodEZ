package in.ac.iiitb.orchestrator.worker;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * All Postgres access for the worker via JdbcTemplate. The orchestrator does NOT own this schema
 * (contest-api's Flyway does) — it only reads source/tests and writes verdicts. The writeback and
 * the running-transition both carry `WHERE status <> 'done'` so a re-delivered job can never
 * overwrite a finished verdict (idempotency).
 */
@Repository
public class SubmissionStore {

    private final JdbcTemplate jdbc;

    public SubmissionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** queued -> running. Returns false if the row is already running/done (or gone). */
    public boolean markRunning(long submissionId) {
        return jdbc.update(
                "UPDATE submissions SET status = 'running' WHERE id = ? AND status = 'queued'",
                submissionId) == 1;
    }

    public JobRow loadJob(long submissionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, user_id, problem_id, contest_id, language, source_code, status, kind "
                            + "FROM submissions WHERE id = ?",
                    (rs, n) -> new JobRow(
                            rs.getLong("id"), rs.getLong("user_id"), rs.getLong("problem_id"),
                            rs.getLong("contest_id"), rs.getString("language"),
                            rs.getString("source_code"), rs.getString("status"), rs.getString("kind")),
                    submissionId);
        } catch (EmptyResultDataAccessException e) {
            return null;   // orphaned stream record (submission deleted) — caller will ACK and move on
        }
    }

    public ProblemRow loadProblem(long problemId) {
        return jdbc.queryForObject(
                "SELECT id, time_limit_ms, memory_limit_mb, test_data_version FROM problems WHERE id = ?",
                (rs, n) -> new ProblemRow(rs.getLong("id"), rs.getInt("time_limit_ms"),
                        rs.getInt("memory_limit_mb"), rs.getInt("test_data_version")),
                problemId);
    }

    /**
     * All test cases for a problem, in order, including which are samples. A Run job filters
     * this down to sample-only before judging (see SubmissionProcessor); a Submit job judges
     * every one of them.
     */
    public List<TestRow> loadTests(long problemId) {
        return jdbc.query(
                "SELECT ordinal, input, expected_output, is_sample FROM test_cases WHERE problem_id = ? ORDER BY ordinal",
                (rs, n) -> new TestRow(rs.getInt("ordinal"), rs.getString("input"),
                        rs.getString("expected_output"), rs.getBoolean("is_sample")),
                problemId);
    }

    /**
     * Idempotent verdict writeback. Returns true if THIS call set the verdict; false means another
     * worker already finished it (status was 'done'), so the caller should simply ACK. When the
     * outcome carries a per-test breakdown (Run jobs only), those rows are inserted too — belt and
     * suspenders on top of the structural guarantee that a Submit outcome's breakdown is always empty.
     */
    public boolean writeVerdict(long submissionId, JudgeOutcome outcome) {
        int rows = jdbc.update(
                "UPDATE submissions SET status = 'done', verdict = ?, failed_test = ?, "
                        + "passed_tests = ?, total_tests = ?, exec_time_ms = ?, memory_kb = ?, judged_at = now() "
                        + "WHERE id = ? AND status <> 'done'",
                outcome.verdict().name(), outcome.failedTest(), outcome.passedTests(), outcome.totalTests(),
                outcome.execTimeMs(), outcome.memoryKb(), submissionId);
        if (rows == 1 && !outcome.tests().isEmpty()) {
            for (TestOutcome t : outcome.tests()) {
                jdbc.update(
                        "INSERT INTO submission_test_results (submission_id, ordinal, verdict, exec_time_ms, memory_kb) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        submissionId, t.ordinal(), t.verdict().name(), t.execTimeMs(), t.memoryKb());
            }
        }
        return rows == 1;
    }
}
