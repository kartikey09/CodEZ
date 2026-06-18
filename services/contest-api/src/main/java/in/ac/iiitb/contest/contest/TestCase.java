package in.ac.iiitb.contest.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "input", nullable = false)
    private String input;

    @Column(name = "expected_output", nullable = false)
    private String expectedOutput;

    @Column(name = "is_sample", nullable = false)
    private boolean sample = false;

    protected TestCase() {
    }

    public TestCase(Long problemId, int ordinal, String input, String expectedOutput, boolean sample) {
        this.problemId = problemId;
        this.ordinal = ordinal;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.sample = sample;
    }

    public Long getId() { return id; }
    public Long getProblemId() { return problemId; }
    public int getOrdinal() { return ordinal; }
    public String getInput() { return input; }
    public String getExpectedOutput() { return expectedOutput; }
    public boolean isSample() { return sample; }
}
