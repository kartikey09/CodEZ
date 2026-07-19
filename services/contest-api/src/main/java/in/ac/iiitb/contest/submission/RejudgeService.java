package in.ac.iiitb.contest.submission;

import in.ac.iiitb.contest.broadcast.StandingsBroadcaster;
import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.error.InvalidRejudgeException;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.web.dto.RejudgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Rejudge: put already-judged submissions back through the Day-7 pipeline.
 *
 * The board rebuild and the push to clients are NOT done here — they already happen. The worker
 * publishes a verdict ping per finished job, contest-api's VerdictListener hears it and asks
 * StandingsBroadcaster for a debounced recompute + publish on ch:standings:{contestId}. So a
 * rejudge only has to re-enqueue correctly and the rest of the chain follows. The one broadcast
 * this class triggers itself is the immediate one, so the board reflects the reset without waiting
 * for the first verdict to land.
 *
 * Two ordering rules make it correct:
 *   1. The status reset must COMMIT BEFORE the stream record is added. The worker skips any job
 *      whose row already reads 'done', so enqueueing first would let it ack the record without
 *      judging. resetForRejudge is @Transactional on the repository, so each chunk commits as it
 *      returns — before anything is enqueued.
 *   2. Only status='done', kind='submit' rows are targeted. Rows still queued/running are already
 *      on their way (and re-enqueueing them would race the in-flight worker); practice Run rows
 *      never touch standings and re-running one would collide with submission_test_results'
 *      (submission_id, ordinal) unique key.
 *
 * The per-user in-flight lock and cooldown are deliberately untouched — this is an admin action,
 * not a student submitting, so it must neither consume nor be blocked by a student's quota.
 */
@Service
public class RejudgeService {

    private static final Logger log = LoggerFactory.getLogger(RejudgeService.class);

    /** Chunk size for the reset UPDATE ... WHERE id IN (...) — keeps the statement a sane size. */
    private static final int CHUNK = 500;

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final ContestRepository contests;
    private final StringRedisTemplate redis;
    private final SubmissionProperties props;
    private final StandingsBroadcaster broadcaster;
    private final int maxBatch;

    public RejudgeService(SubmissionRepository submissions, ProblemRepository problems,
                          ContestRepository contests, StringRedisTemplate redis,
                          SubmissionProperties props, StandingsBroadcaster broadcaster,
                          @Value("${app.rejudge.max-batch:5000}") int maxBatch) {
        this.submissions = submissions;
        this.problems = problems;
        this.contests = contests;
        this.redis = redis;
        this.props = props;
        this.broadcaster = broadcaster;
        this.maxBatch = maxBatch;
    }

    /** One submission. Refuses anything not already judged, and refuses practice Run rows. */
    public RejudgeResult rejudgeSubmission(long submissionId) {
        Submission s = submissions.findById(submissionId).orElseThrow(NotFoundException::new);
        if (!"submit".equals(s.getKind())) {
            throw new InvalidRejudgeException("only graded submissions can be rejudged, not practice runs");
        }
        if (!"done".equals(s.getStatus())) {
            throw new InvalidRejudgeException("submission is not judged yet (status " + s.getStatus() + ")");
        }
        return requeue("submission", submissionId, s.getContestId(), List.of(submissionId));
    }

    /** Every judged attempt at one problem — the usual case after fixing test data. */
    public RejudgeResult rejudgeProblem(long problemId) {
        Problem p = problems.findById(problemId).orElseThrow(NotFoundException::new);
        List<Long> ids = submissions.findRejudgeTargetsByProblem(problemId);
        return requeue("problem", problemId, p.getContestId(), ids);
    }

    /** Every judged attempt in a contest. */
    public RejudgeResult rejudgeContest(long contestId) {
        Contest c = contests.findById(contestId).orElseThrow(NotFoundException::new);
        List<Long> ids = submissions.findRejudgeTargetsByContest(contestId);
        return requeue("contest", c.getId(), c.getId(), ids);
    }

    private RejudgeResult requeue(String scope, long targetId, long contestId, List<Long> ids) {
        if (ids.isEmpty()) {
            return new RejudgeResult(scope, targetId, 0);
        }
        if (ids.size() > maxBatch) {
            throw new InvalidRejudgeException("refusing to rejudge " + ids.size()
                + " submissions at once (cap is " + maxBatch
                + "); rejudge problem by problem, or raise app.rejudge.max-batch");
        }

        // 1) reset in committed chunks — see the ordering note in the class javadoc
        for (int from = 0; from < ids.size(); from += CHUNK) {
            submissions.resetForRejudge(ids.subList(from, Math.min(from + CHUNK, ids.size())));
        }

        // 2) enqueue. The worker reads only submissionId from the record and loads the rest from
        //    Postgres, so the record stays a pointer — source code never rides on the stream.
        for (Long id : ids) {
            // Typed as Record (not MapRecord) so this binds to StreamOperations#add(Record), the
            // single abstract stream-add method — keeps dispatch unambiguous under test mocking,
            // where the MapRecord overload (a default method) isn't routed through it.
            Record<String, ?> record = StreamRecords
                .mapBacked(Map.of("submissionId", Long.toString(id)))
                .withStreamKey(props.streamKey());
            redis.opsForStream().add(record);
        }

        // 3) reflect the reset on the board immediately; verdicts will drive further updates
        broadcaster.schedule(contestId);

        log.info("Rejudge {} {}: requeued {} submissions", scope, targetId, ids.size());
        return new RejudgeResult(scope, targetId, ids.size());
    }
}
