package in.ac.iiitb.contest.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One test's outcome for a "Run" submission. Only ever written by the orchestrator for
 * kind='run' rows, which by construction only ever judge sample tests — so this table
 * structurally never carries hidden-test results.
 */
@Entity
@Table(name = "submission_test_results")
public class SubmissionTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false)
    private String verdict;

    @Column(name = "exec_time_ms")
    private Integer execTimeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    protected SubmissionTestResult() {
    }

    public Long getId() { return id; }
    public Long getSubmissionId() { return submissionId; }
    public int getOrdinal() { return ordinal; }
    public String getVerdict() { return verdict; }
    public Integer getExecTimeMs() { return execTimeMs; }
    public Integer getMemoryKb() { return memoryKb; }
}
