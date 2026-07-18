package in.ac.iiitb.contest.web;

import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.session.AuthContext;
import in.ac.iiitb.contest.submission.Submission;
import in.ac.iiitb.contest.submission.SubmissionRepository;
import in.ac.iiitb.contest.submission.SubmissionService;
import in.ac.iiitb.contest.submission.SubmissionTestResult;
import in.ac.iiitb.contest.submission.SubmissionTestResultRepository;
import in.ac.iiitb.contest.web.dto.SubmissionAccepted;
import in.ac.iiitb.contest.web.dto.SubmissionStatusResponse;
import in.ac.iiitb.contest.web.dto.SubmissionSummary;
import in.ac.iiitb.contest.web.dto.SubmitRequest;
import in.ac.iiitb.contest.web.dto.TestResultDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The user is always taken from the session the filter resolved — never from the body —
 * so a client cannot submit or read on behalf of someone else. GET /mine is a literal
 * path and is matched ahead of the /{id} pattern.
 */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService service;
    private final SubmissionRepository submissions;
    private final SubmissionTestResultRepository testResults;

    public SubmissionController(SubmissionService service, SubmissionRepository submissions,
                                SubmissionTestResultRepository testResults) {
        this.service = service;
        this.submissions = submissions;
        this.testResults = testResults;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmissionAccepted submit(@Valid @RequestBody SubmitRequest req, HttpServletRequest http) {
        long userId = AuthContext.user(http).userId();
        long id = service.submit(userId, req.problemId(), req.language(), req.sourceCode());
        return new SubmissionAccepted(id, "queued");
    }

    @GetMapping("/mine")
    public List<SubmissionSummary> mine(HttpServletRequest http) {
        long userId = AuthContext.user(http).userId();
        return submissions.findByUserIdAndKindOrderByCreatedAtDesc(userId, "submit").stream()
            .map(s -> new SubmissionSummary(s.getId(), s.getProblemId(), s.getLanguage(),
                s.getStatus(), s.getVerdict(), s.getCreatedAt()))
            .toList();
    }

    @GetMapping("/{id}")
    public SubmissionStatusResponse status(@PathVariable long id, HttpServletRequest http) {
        long userId = AuthContext.user(http).userId();
        Submission s = submissions.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
        return new SubmissionStatusResponse(s.getId(), s.getProblemId(), s.getLanguage(), s.getStatus(),
            s.getVerdict(), s.getKind(), s.getPassedTests(), s.getTotalTests(),
            s.getExecTimeMs(), s.getMemoryKb(), s.getCreatedAt(), s.getJudgedAt(), testBreakdown(s));
    }

    /** Only ever non-empty for kind='run' — a Submit row has nothing written here to leak. */
    private List<TestResultDto> testBreakdown(Submission s) {
        if (!"run".equals(s.getKind())) {
            return List.of();
        }
        List<SubmissionTestResult> rows = testResults.findBySubmissionIdOrderByOrdinal(s.getId());
        List<TestResultDto> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            SubmissionTestResult r = rows.get(i);
            out.add(new TestResultDto(i + 1, r.getVerdict(), r.getExecTimeMs(), r.getMemoryKb()));
        }
        return out;
    }
}
