package in.ac.iiitb.contest.web.admin;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.contest.TestCase;
import in.ac.iiitb.contest.contest.TestCaseRepository;
import in.ac.iiitb.contest.error.InvalidProblemUpdateException;
import in.ac.iiitb.contest.error.NoContestFoundException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.submission.SubmissionRepository;
import in.ac.iiitb.contest.web.dto.CreateProblemRequest;
import in.ac.iiitb.contest.web.dto.ProblemAdminDetail;
import in.ac.iiitb.contest.web.dto.ProblemAdminResponse;
import in.ac.iiitb.contest.web.dto.TestCaseUpload;
import in.ac.iiitb.contest.web.dto.TestCaseUploadRequest;
import in.ac.iiitb.contest.web.dto.TestCaseUploadResult;
import in.ac.iiitb.contest.web.dto.TestCaseView;
import in.ac.iiitb.contest.web.dto.UpdateProblemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Problem + test-case authoring (Day 14 extends the Day-4 create-only controller).
 *
 * On hidden test data: it still never leaves the server. The list endpoint returns full input and
 * expected output for SAMPLES only (students already see those); for hidden tests it returns
 * ordinal, sample flag and byte sizes, which is enough to spot an empty or truncated upload
 * without turning an admin session into a way to read the answer key. Editing a hidden test is
 * therefore delete + re-add.
 *
 * Every mutation of test data bumps problems.test_data_version. The orchestrator's TestCache is
 * keyed by problemId + that version, so skipping the bump would leave workers judging against the
 * tests they cached before the edit — the exact failure a rejudge is meant to fix.
 */
@RestController
@RequestMapping("/api/admin/problems")
public class AdminProblemController {

    private final ContestRepository contests;
    private final ProblemRepository problems;
    private final TestCaseRepository tests;
    private final SubmissionRepository submissions;

    public AdminProblemController(ContestRepository contests, ProblemRepository problems,
                                  TestCaseRepository tests, SubmissionRepository submissions) {
        this.contests = contests;
        this.problems = problems;
        this.tests = tests;
        this.submissions = submissions;
    }

    @GetMapping
    public List<ProblemAdminResponse> list(@RequestParam long contestId) {
        return problems.findByContestIdOrderByLabel(contestId).stream()
            .map(AdminProblemController::toResponse)
            .toList();
    }

    @GetMapping("/{id}")
    public ProblemAdminDetail detail(@PathVariable long id) {
        Problem p = problems.findById(id).orElseThrow(NotFoundException::new);
        List<TestCaseView> views = tests.findByProblemIdOrderByOrdinal(id).stream()
            .map(AdminProblemController::toView)
            .toList();
        return new ProblemAdminDetail(p.getId(), p.getContestId(), p.getLabel(), p.getTitle(),
            p.getStatementMd(), p.getTimeLimitMs(), p.getMemoryLimitMb(), p.getTestDataVersion(),
            views.size(), submissions.countPendingByProblem(id), views);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemAdminResponse create(@Valid @RequestBody CreateProblemRequest req) {
        Contest c = contests.findById(req.contestId()).orElseThrow(NoContestFoundException::new);
        int tl = req.timeLimitMs() > 0 ? req.timeLimitMs() : 1000;
        int ml = req.memoryLimitMb() > 0 ? req.memoryLimitMb() : 256;
        Problem p = problems.save(new Problem(c.getId(), req.label(), req.title(), req.statementMd(), tl, ml));
        return toResponse(p);
    }

    /** Partial edit — only non-null fields are applied. Does NOT touch test data or its version. */
    @PatchMapping("/{id}")
    public ProblemAdminResponse update(@PathVariable long id, @RequestBody UpdateProblemRequest req) {
        Problem p = problems.findById(id).orElseThrow(NotFoundException::new);

        if (req.label() != null) {
            requireText(req.label(), "label");
            p.setLabel(req.label().trim());
        }
        if (req.title() != null) {
            requireText(req.title(), "title");
            p.setTitle(req.title().trim());
        }
        if (req.statementMd() != null) {
            requireText(req.statementMd(), "statementMd");
            p.setStatementMd(req.statementMd());
        }
        if (req.timeLimitMs() != null) {
            requirePositive(req.timeLimitMs(), "timeLimitMs");
            p.setTimeLimitMs(req.timeLimitMs());
        }
        if (req.memoryLimitMb() != null) {
            requirePositive(req.memoryLimitMb(), "memoryLimitMb");
            p.setMemoryLimitMb(req.memoryLimitMb());
        }

        problems.save(p);
        return toResponse(p);
    }

    /** Test-case inventory: full data for samples, metadata only for hidden tests. */
    @GetMapping("/{id}/test-cases")
    public List<TestCaseView> listTests(@PathVariable long id) {
        problems.findById(id).orElseThrow(NotFoundException::new);
        return tests.findByProblemIdOrderByOrdinal(id).stream()
            .map(AdminProblemController::toView)
            .toList();
    }

    /** Append test cases. Rejects ordinals that already exist, and bumps the test-data version. */
    @Transactional
    @PostMapping("/{id}/test-cases")
    public TestCaseUploadResult uploadTests(@PathVariable long id, @Valid @RequestBody TestCaseUploadRequest req) {
        Problem p = problems.findById(id).orElseThrow(NotFoundException::new);

        Set<Integer> existing = new HashSet<>();
        for (TestCase t : tests.findByProblemIdOrderByOrdinal(id)) {
            existing.add(t.getOrdinal());
        }
        Set<Integer> incoming = new HashSet<>();
        for (TestCaseUpload t : req.tests()) {
            if (!incoming.add(t.ordinal())) {
                throw new InvalidProblemUpdateException("duplicate ordinal " + t.ordinal() + " in this upload");
            }
            if (existing.contains(t.ordinal())) {
                throw new InvalidProblemUpdateException(
                    "ordinal " + t.ordinal() + " already exists - delete it first to replace it");
            }
        }

        int added = 0;
        for (TestCaseUpload t : req.tests()) {
            tests.save(new TestCase(p.getId(), t.ordinal(), t.input(), t.expectedOutput(), t.sample()));
            added++;
        }

        p.bumpTestDataVersion();
        problems.save(p);
        return new TestCaseUploadResult(added);
    }

    /** Remove one test case by ordinal, and bump the test-data version. */
    @Transactional
    @DeleteMapping("/{id}/test-cases/{ordinal}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTest(@PathVariable long id, @PathVariable int ordinal) {
        Problem p = problems.findById(id).orElseThrow(NotFoundException::new);
        TestCase t = tests.findByProblemIdAndOrdinal(id, ordinal).orElseThrow(NotFoundException::new);
        tests.delete(t);
        p.bumpTestDataVersion();
        problems.save(p);
    }

    private static ProblemAdminResponse toResponse(Problem p) {
        return new ProblemAdminResponse(p.getId(), p.getContestId(), p.getLabel(), p.getTitle(),
            p.getTimeLimitMs(), p.getMemoryLimitMb());
    }

    private static TestCaseView toView(TestCase t) {
        int inBytes = t.getInput().getBytes(StandardCharsets.UTF_8).length;
        int outBytes = t.getExpectedOutput().getBytes(StandardCharsets.UTF_8).length;
        // Samples are already public; hidden test data is reported by size only.
        return new TestCaseView(t.getId(), t.getOrdinal(), t.isSample(), inBytes, outBytes,
            t.isSample() ? t.getInput() : null,
            t.isSample() ? t.getExpectedOutput() : null);
    }

    private static void requireText(String value, String field) {
        if (value.trim().isEmpty()) {
            throw new InvalidProblemUpdateException(field + " must not be blank");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new InvalidProblemUpdateException(field + " must be greater than zero");
        }
    }
}
