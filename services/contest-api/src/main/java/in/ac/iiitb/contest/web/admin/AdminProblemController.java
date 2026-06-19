package in.ac.iiitb.contest.web.admin;

import in.ac.iiitb.contest.contest.*;
import in.ac.iiitb.contest.error.NoContestFoundException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/problems")
public class AdminProblemController {
    private final ContestRepository contests;
    private final ProblemRepository problems;
    private final TestCaseRepository tests;

    public AdminProblemController(ContestRepository contests, ProblemRepository problems, TestCaseRepository tests) {
        this.contests = contests;
        this.problems = problems;
        this.tests = tests;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemAdminResponse create(@Valid @RequestBody CreateProblemRequest req) {
        Contest c = contests.findById(req.contestId()).orElseThrow(NoContestFoundException::new);
        int tl = req.timeLimitMs() > 0 ? req.timeLimitMs() : 1000;
        int ml = req.memoryLimitMb() > 0 ? req.memoryLimitMb() : 256;
        Problem p = problems.save(new Problem(req.contestId(), req.label(), req.title(), req.statementMd(), tl, ml));
        return new ProblemAdminResponse(p.getId(), p.getContestId(), p.getLabel(), p.getTitle(),
            p.getTimeLimitMs(), p.getMemoryLimitMb());
    }

    /** Append test cases (write-only — nothing here ever reads hidden test data back out). */
    @PostMapping("/{id}/test-cases")
    public TestCaseUploadResult uploadTests(@PathVariable long id, @Valid @RequestBody TestCaseUploadRequest req) {
        Problem p = problems.findById(id).orElseThrow(NotFoundException::new);
        int added = 0;
        for (TestCaseUpload t : req.tests()) {
            tests.save(new TestCase(p.getId(), t.ordinal(), t.input(), t.expectedOutput(), t.sample()));
            added++;
        }
        return new TestCaseUploadResult(added);
    }
}
