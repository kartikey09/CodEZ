package in.ac.iiitb.contest.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import in.ac.iiitb.contest.broadcast.StandingsBroadcaster;
import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.error.InvalidRejudgeException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.RejudgeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test for the rejudge rules. Deliberately not a Spring slice — the value here is the
 * selection + ordering logic, which is pure Java over mocked collaborators.
 */
class RejudgeServiceTest {

    private SubmissionRepository submissions;
    private ProblemRepository problems;
    private ContestRepository contests;
    private StringRedisTemplate redis;
    private StreamOperations<String, Object, Object> streamOps;
    private StandingsBroadcaster broadcaster;
    private RejudgeService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        submissions = mock(SubmissionRepository.class);
        problems = mock(ProblemRepository.class);
        contests = mock(ContestRepository.class);
        redis = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        broadcaster = mock(StandingsBroadcaster.class);
        when(redis.opsForStream()).thenReturn(streamOps);

        SubmissionProperties props = new SubmissionProperties(
            List.of("c", "cpp", "java", "python"), 65536, 10000L, 120L, 5L, "subq");
        service = new RejudgeService(submissions, problems, contests, redis, props, broadcaster, 5000);
    }

    private static Submission submission(long id, long contestId, String status, SubmissionKind kind) {
        Submission s = new Submission(7L, 3L, contestId, "python", "print(1)", kind);
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }

    private static Problem problem(long id, long contestId) {
        Problem p = new Problem(contestId, "A", "Sum", "Add them.", 1000, 256);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static Contest contest(long id) {
        Contest c = new Contest("Weekly", java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600), "running");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void rejudgeSubmissionResetsThenEnqueuesThenBroadcasts() {
        when(submissions.findById(42L)).thenReturn(Optional.of(submission(42, 9, "done", SubmissionKind.SUBMIT)));

        RejudgeResult result = service.rejudgeSubmission(42L);

        assertEquals(1, result.requeued());
        assertEquals("submission", result.scope());

        // The reset MUST commit before the record is enqueued, or the worker sees a 'done' row and acks it.
        InOrder order = inOrder(submissions, streamOps, broadcaster);
        order.verify(submissions).resetForRejudge(List.of(42L));
        order.verify(streamOps).add(any(Record.class));
        order.verify(broadcaster).schedule(9L);
    }

    @Test
    void practiceRunRowIsRefused() {
        when(submissions.findById(42L)).thenReturn(Optional.of(submission(42, 9, "done", SubmissionKind.RUN)));

        assertThrows(InvalidRejudgeException.class, () -> service.rejudgeSubmission(42L));
        verify(submissions, never()).resetForRejudge(any());
        verify(streamOps, never()).add(any(Record.class));
    }

    @Test
    void unjudgedSubmissionIsRefused() {
        when(submissions.findById(42L)).thenReturn(Optional.of(submission(42, 9, "queued", SubmissionKind.SUBMIT)));

        assertThrows(InvalidRejudgeException.class, () -> service.rejudgeSubmission(42L));
        verify(streamOps, never()).add(any(Record.class));
    }

    @Test
    void unknownSubmissionIs404() {
        when(submissions.findById(42L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.rejudgeSubmission(42L));
    }

    @Test
    void rejudgeProblemEnqueuesEveryTarget() {
        when(problems.findById(3L)).thenReturn(Optional.of(problem(3, 9)));
        when(submissions.findRejudgeTargetsByProblem(3L)).thenReturn(List.of(1L, 2L, 3L));

        RejudgeResult result = service.rejudgeProblem(3L);

        assertEquals(3, result.requeued());
        verify(streamOps, times(3)).add(any(Record.class));
        verify(broadcaster).schedule(9L);
    }

    @Test
    void nothingToRejudgeIsZeroNotAnError() {
        when(problems.findById(3L)).thenReturn(Optional.of(problem(3, 9)));
        when(submissions.findRejudgeTargetsByProblem(3L)).thenReturn(List.of());

        RejudgeResult result = service.rejudgeProblem(3L);

        assertEquals(0, result.requeued());
        verify(streamOps, never()).add(any(Record.class));
        verify(broadcaster, never()).schedule(anyLong());
    }

    @Test
    void batchLargerThanCapIsRefused() {
        SubmissionProperties props = new SubmissionProperties(List.of("python"), 65536, 10000L, 120L, 5L, "subq");
        RejudgeService capped = new RejudgeService(submissions, problems, contests, redis, props, broadcaster, 2);
        when(contests.findById(9L)).thenReturn(Optional.of(contest(9)));
        when(submissions.findRejudgeTargetsByContest(9L)).thenReturn(List.of(1L, 2L, 3L));

        assertThrows(InvalidRejudgeException.class, () -> capped.rejudgeContest(9L));
        verify(submissions, never()).resetForRejudge(any());
    }

    @Test
    void rejudgeContestUsesContestScope() {
        when(contests.findById(9L)).thenReturn(Optional.of(contest(9)));
        when(submissions.findRejudgeTargetsByContest(9L)).thenReturn(List.of(5L, 6L));

        RejudgeResult result = service.rejudgeContest(9L);

        assertEquals("contest", result.scope());
        assertEquals(2, result.requeued());
        verify(streamOps, times(2)).add(any(Record.class));
    }
}
