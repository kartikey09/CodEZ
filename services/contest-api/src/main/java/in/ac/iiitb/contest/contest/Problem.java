package in.ac.iiitb.contest.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain id, not a JPA relationship — keeps reads simple and the boundary explicit.
    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "statement_md", nullable = false)
    private String statementMd;

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs = 1000;

    @Column(name = "memory_limit_mb", nullable = false)
    private int memoryLimitMb = 256;

    @Column(name = "test_data_version", nullable = false)
    private int testDataVersion = 1;

    protected Problem() {
    }

    public Problem(Long contestId, String label, String title, String statementMd,
                   int timeLimitMs, int memoryLimitMb) {
        this.contestId = contestId;
        this.label = label;
        this.title = title;
        this.statementMd = statementMd;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
    }

    public Long getId() { return id; }
    public Long getContestId() { return contestId; }
    public String getLabel() { return label; }
    public String getTitle() { return title; }
    public String getStatementMd() { return statementMd; }
    public int getTimeLimitMs() { return timeLimitMs; }
    public int getMemoryLimitMb() { return memoryLimitMb; }
    public int getTestDataVersion() { return testDataVersion; }

    // ----- Day 14: the problem-authoring UI edits problems in place -----

    public void setLabel(String label) { this.label = label; }
    public void setTitle(String title) { this.title = title; }
    public void setStatementMd(String statementMd) { this.statementMd = statementMd; }
    public void setTimeLimitMs(int timeLimitMs) { this.timeLimitMs = timeLimitMs; }
    public void setMemoryLimitMb(int memoryLimitMb) { this.memoryLimitMb = memoryLimitMb; }

    /**
     * Must be called on EVERY change to this problem's test data. The orchestrator's TestCache is
     * keyed by problemId + testDataVersion, so without a bump a worker keeps serving the tests it
     * cached before the edit — and a rejudge would silently re-run against stale data.
     */
    public void bumpTestDataVersion() { this.testDataVersion++; }
}
