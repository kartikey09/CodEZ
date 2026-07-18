package in.ac.iiitb.contest.submission;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Maps to the submissions table (Flyway V2). Created queued; the orchestrator (Day 7) fills the verdict. */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Column(nullable = false)
    private String status;

    private String verdict;

    @Column(name = "failed_test")
    private Integer failedTest;

    @Column(name = "exec_time_ms")
    private Integer execTimeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    @Column(nullable = false)
    private String kind;

    @Column(name = "passed_tests")
    private Integer passedTests;

    @Column(name = "total_tests")
    private Integer totalTests;

    // DB fills created_at via DEFAULT now(); we never write it.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "judged_at")
    private Instant judgedAt;

    protected Submission() {
    }

    public Submission(Long userId, Long problemId, Long contestId, String language, String sourceCode,
                       SubmissionKind kind) {
        this.userId = userId;
        this.problemId = problemId;
        this.contestId = contestId;
        this.language = language;
        this.sourceCode = sourceCode;
        this.status = "queued";
        this.kind = kind.dbValue();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public Long getContestId() {
        return contestId;
    }

    public String getLanguage() {
        return language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getStatus() {
        return status;
    }

    public String getVerdict() {
        return verdict;
    }

    public Integer getFailedTest() {
        return failedTest;
    }

    public Integer getExecTimeMs() {
        return execTimeMs;
    }

    public Integer getMemoryKb() {
        return memoryKb;
    }

    public String getKind() {
        return kind;
    }

    public Integer getPassedTests() {
        return passedTests;
    }

    public Integer getTotalTests() {
        return totalTests;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }
}
