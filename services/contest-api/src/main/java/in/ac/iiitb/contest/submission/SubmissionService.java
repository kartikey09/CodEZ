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
 * cheap/local validation first (language, size), then state (window, problem), then the two
 * Redis guards. The in-flight lock is taken before the cooldown; if the cooldown then rejects,
 * the in-flight lock is released so a rejected attempt never holds it.
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

    public SubmissionService(ContestRepository contests, ProblemRepository problems,
                             SubmissionRepository submissions, StringRedisTemplate redis,
                             SubmissionProperties props) {
        this.contests = contests;
        this.problems = problems;
        this.submissions = submissions;
        this.redis = redis;
        this.props = props;
    }

    public long submit(long userId, long problemId, String language, String sourceCode) {
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
        // 5. one-in-flight lock (SET NX EX): blocks a second submission while one is being judged
        String inflightKey = "inflight:" + userId;
        Boolean tookInflight = redis.opsForValue()
                .setIfAbsent(inflightKey, Long.toString(now.toEpochMilli()),
                        Duration.ofSeconds(props.inflightTtlSeconds()));
        if (!Boolean.TRUE.equals(tookInflight)) {
            throw new SubmissionInFlightException();
        }
        // 6. cooldown (SET NX PX): rate-limits how often a user may submit
        String cooldownKey = "cooldown:" + userId;
        Boolean tookCooldown = redis.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofMillis(props.cooldownMs()));
        if (!Boolean.TRUE.equals(tookCooldown)) {
            redis.delete(inflightKey);          // don't let a rejected attempt hold the in-flight lock
            throw new CooldownActiveException();
        }
        // 7. persist as queued
        try{
            Submission saved = transactionalPersist(userId, problemId, contest.getId(), language, sourceCode);
            // 8. enqueue routing record (ids only)
            redis.opsForStream().add(StreamRecords.mapBacked(Map.of(
                            "submissionId", Long.toString(saved.getId()),
                            "problemId", Long.toString(problemId),
                            "contestId", Long.toString(contest.getId()),
                            "userId", Long.toString(userId),
                            "language", language))
                    .withStreamKey(props.streamKey()));
            return saved.getId();
        } catch (Exception e) {
            redis.delete(inflightKey);
            throw new SubmissionDatabaseException(e);
        }
    }

    @Transactional
    public Submission transactionalPersist(long userId, long problemId, long contestId, String language, String sourceCode) {
        return submissions.save(new Submission(userId, problemId, contestId, language, sourceCode));
    }
}
