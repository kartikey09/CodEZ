package in.ac.iiitb.contest.submission;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.error.*;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Order of admission checks is deliberate:
 * cheap/local validation first (language, size), then state (window, problem), then the
 * in-flight lock — shared by both Submit and Run, so a user can never have two judge jobs
 * running concurrently. Submit additionally takes the cooldown lock; Run additionally checks
 * its own independent rate limit ({@link RunRateLimiter}, 3 per rolling 12s). Either rejection
 * releases the in-flight lock it just took, so a rejected attempt never holds it.
 *
 * On success: insert the row as 'queued', then XADD a lightweight routing record to the stream.
 * The record carries only ids — the orchestrator loads source + tests from Postgres, so source
 * code never rides on the stream.
 */
@Service
public class SubmissionService {

    private final ContestRepository contests;
    private final ProblemRepository problems;
    private final SubmissionRepository submissions;
    private final StringRedisTemplate redis;
    private final SubmissionProperties props;
    private final RunRateLimiter runRateLimiter;

    public SubmissionService(ContestRepository contests, ProblemRepository problems,
                             SubmissionRepository submissions, StringRedisTemplate redis,
                             SubmissionProperties props, RunRateLimiter runRateLimiter) {
        this.contests = contests;
        this.problems = problems;
        this.submissions = submissions;
        this.redis = redis;
        this.props = props;
        this.runRateLimiter = runRateLimiter;
    }

    public long submit(long userId, long problemId, String language, String sourceCode) {
        Admission a = admit(userId, problemId, language, sourceCode);
        // cooldown (SET NX PX): rate-limits how often a user may submit for real
        String cooldownKey = "cooldown:" + userId;
        Boolean tookCooldown = redis.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofMillis(props.cooldownMs()));
        if (!Boolean.TRUE.equals(tookCooldown)) {
            redis.delete(a.inflightKey());
            throw new CooldownActiveException();
        }
        return enqueue(userId, problemId, language, sourceCode, a, SubmissionKind.SUBMIT);
    }

    /** Practice run against sample tests only. Its own 3-per-12s limiter; never touches the cooldown key. */
    public long run(long userId, long problemId, String language, String sourceCode) {
        Admission a = admit(userId, problemId, language, sourceCode);
        try {
            runRateLimiter.check(userId);
        } catch (RunRateLimitExceededException e) {
            redis.delete(a.inflightKey());
            throw e;
        }
        return enqueue(userId, problemId, language, sourceCode, a, SubmissionKind.RUN);
    }

    /** Shared admission gate for both Submit and Run: language, size, contest window, ownership, in-flight lock. */
    private Admission admit(long userId, long problemId, String language, String sourceCode) {
        // 1. language allowlist
        if (!props.allowedLanguages().contains(language)) {
            throw new LanguageNotAllowedException();
        }
        // 2. source size (bytes, not chars)
        if (sourceCode.getBytes(StandardCharsets.UTF_8).length > props.maxSourceBytes()) {
            throw new SourceTooLargeException();
        }
        // 3. running contest + open window (+ grace past the end)
        Contest contest = contests.findFirstByStateOrderByStartsAtDesc("running").orElseThrow(NoContestFoundException::new);
        Instant now = Instant.now();
        if (now.isBefore(contest.getStartsAt())) {
            throw new ContestNotStartedException();
        }
        if (now.isAfter(contest.getEndsAt().plusSeconds(props.graceSeconds()))) {
            throw new ContestEndedException();
        }
        // 4. the problem must belong to that contest
        Problem problem = problems.findById(problemId).orElseThrow(NotFoundException::new);
        if (!problem.getContestId().equals(contest.getId())) {
            throw new NotFoundException();
        }
        // 5. one-in-flight lock (SET NX EX): blocks a second judge job (Submit or Run) while one is running
        String inflightKey = "inflight:" + userId;
        Boolean tookInflight = redis.opsForValue()
                .setIfAbsent(inflightKey, Long.toString(now.toEpochMilli()),
                        Duration.ofSeconds(props.inflightTtlSeconds()));
        if (!Boolean.TRUE.equals(tookInflight)) {
            throw new SubmissionInFlightException();
        }
        return new Admission(problem, contest, inflightKey);
    }

    private long enqueue(long userId, long problemId, String language, String sourceCode,
                         Admission a, SubmissionKind kind) {
        try {
            Submission saved = transactionalPersist(userId, problemId, a.contest().getId(), language, sourceCode, kind);
            redis.opsForStream().add(StreamRecords.mapBacked(Map.of(
                            "submissionId", Long.toString(saved.getId()),
                            "problemId", Long.toString(problemId),
                            "contestId", Long.toString(a.contest().getId()),
                            "userId", Long.toString(userId),
                            "language", language,
                            "kind", kind.dbValue()))
                    .withStreamKey(props.streamKey()));
            return saved.getId();
        } catch (Exception e) {
            redis.delete(a.inflightKey());
            throw new SubmissionDatabaseException(e);
        }
    }

    @Transactional
    public Submission transactionalPersist(long userId, long problemId, long contestId, String language,
                                           String sourceCode, SubmissionKind kind) {
        return submissions.save(new Submission(userId, problemId, contestId, language, sourceCode, kind));
    }

    private record Admission(Problem problem, Contest contest, String inflightKey) {
    }
}
