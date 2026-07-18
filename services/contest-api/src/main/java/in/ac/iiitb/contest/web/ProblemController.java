package in.ac.iiitb.contest.web;

import java.time.Instant;
import java.util.List;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.contest.TestCaseRepository;
import in.ac.iiitb.contest.error.ContestNotStartedException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.session.AuthContext;
import in.ac.iiitb.contest.submission.SubmissionService;
import in.ac.iiitb.contest.web.dto.ProblemDetail;
import in.ac.iiitb.contest.web.dto.ProblemSummary;
import in.ac.iiitb.contest.web.dto.RunRequest;
import in.ac.iiitb.contest.web.dto.SampleTest;
import in.ac.iiitb.contest.web.dto.SubmissionAccepted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ContestRepository contests;
    private final ProblemRepository problems;
    private final TestCaseRepository tests;
    private final SubmissionService submissionService;

    public ProblemController(ContestRepository contests, ProblemRepository problems, TestCaseRepository tests,
                             SubmissionService submissionService) {
        this.contests = contests;
        this.problems = problems;
        this.tests = tests;
        this.submissionService = submissionService;
    }

    @GetMapping("/problems")
    public List<ProblemSummary> list() {
        Contest contest = currentContest();
        requireStarted(contest);
        return problems.findByContestIdOrderByLabel(contest.getId()).stream()
            .map(p -> new ProblemSummary(p.getId(), p.getLabel(), p.getTitle()))
            .toList();
    }

    @GetMapping("/problems/{id}")
    public ProblemDetail one(@PathVariable long id) {
        Problem p = problems.findById(id).orElseThrow(NotFoundException::new);
        Contest contest = contests.findById(p.getContestId()).orElseThrow(NotFoundException::new);
        requireStarted(contest);

        List<SampleTest> samples = tests.findByProblemIdAndSampleTrueOrderByOrdinal(id).stream()
            .map(t -> new SampleTest(t.getOrdinal(), t.getInput(), t.getExpectedOutput()))
            .toList();

        return new ProblemDetail(p.getId(), p.getLabel(), p.getTitle(), p.getStatementMd(),
            p.getTimeLimitMs(), p.getMemoryLimitMb(), samples);
    }

    /** Practice run against sample tests only — never touches Submit's cooldown, has its own rate limit. */
    @PostMapping("/problems/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmissionAccepted run(@PathVariable long id, @Valid @RequestBody RunRequest req, HttpServletRequest http) {
        long userId = AuthContext.user(http).userId();
        long submissionId = submissionService.run(userId, id, req.language(), req.sourceCode());
        return new SubmissionAccepted(submissionId, "queued");
    }

    private Contest currentContest() {
        return contests.findFirstByStateOrderByStartsAtDesc("running").orElseThrow(NotFoundException::new);
    }

    private void requireStarted(Contest contest) {
        if (Instant.now().isBefore(contest.getStartsAt())) {
            throw new ContestNotStartedException();
        }
    }
}
