package in.ac.iiitb.orchestrator.worker;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ac.iiitb.orchestrator.judge0.Judge0Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The per-record judging logic, extracted from the Day-7 worker so that BOTH the live read loop
 * ({@link SubmissionWorker}) and the {@link Reclaimer} run it through one path.
 *
 * {@link #process} judges a record and, on a Judge0 transport failure, records it against the
 * circuit breaker and leaves the record UNACKED (the reclaimer will revisit it). {@link #poison}
 * is the terminal path for a record that has been redelivered too many times: it writes IE,
 * acknowledges, publishes and releases the lock without touching Judge0.
 *
 * The only stream fields read are {@code submissionId} and (P0-4) the in-flight lock's owner token;
 * everything else comes from Postgres.
 */
@Component
public class SubmissionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SubmissionProcessor.class);
    static final String FIELD_SUBMISSION_ID = "submissionId";
    /** P0-4: the per-attempt owner token contest-api put on the record when it took the in-flight lock. */
    static final String FIELD_INFLIGHT_TOKEN = "inflightToken";

    private final StringRedisTemplate redis;
    private final SubmissionStore store;
    private final TestCache tests;
    private final JudgeService judge;
    private final Judge0CircuitBreaker breaker;
    private final InflightLock inflightLock;
    private final WorkerProperties props;
    private final ObjectMapper json;

    public SubmissionProcessor(StringRedisTemplate redis, SubmissionStore store, TestCache tests,
                               JudgeService judge, Judge0CircuitBreaker breaker, InflightLock inflightLock,
                               WorkerProperties props, ObjectMapper json) {
        this.redis = redis;
        this.store = store;
        this.tests = tests;
        this.judge = judge;
        this.breaker = breaker;
        this.inflightLock = inflightLock;
        this.props = props;
        this.json = json;
    }

    /** Judge one record end to end. Leaves the record unacked on a Judge0 outage so it can be reclaimed. */
    public void process(MapRecord<String, Object, Object> record) {
        Long submissionId = submissionId(record);
        if (submissionId == null) {
            ack(record);
            return;
        }
        try {
            JobRow job = store.loadJob(submissionId);
            if (job == null) {            // submission vanished
                ack(record);
                return;
            }
            if ("done".equals(job.status())) {   // already judged (re-delivery) — idempotent
                ack(record);
                return;
            }

            store.markRunning(submissionId);
            // P0-4: judging is starting now, so re-take the lock's TTL from this point -- the time the job
            // spent waiting in the queue no longer counts against the window it has to finish judging in.
            String token = inflightToken(record);
            if (token != null) {
                inflightLock.refreshIfOwner(inflightKey(job.userId()), token, props.inflightTtlSeconds());
            }
            ProblemRow problem = store.loadProblem(job.problemId());
            boolean isRun = "run".equals(job.kind());
            List<TestRow> all = tests.get(job.problemId(), problem.testDataVersion());
            // A Run job only ever judges sample tests — this filter is the structural guarantee
            // that hidden tests are never touched (let alone exposed) by a practice run.
            List<TestRow> toJudge = isRun ? all.stream().filter(TestRow::sample).toList() : all;
            JudgeOutcome outcome = judge.judge(job, problem, toJudge, isRun);
            store.writeVerdict(submissionId, outcome);

            ack(record);
            publish(job.userId(), submissionId, outcome.verdict());
            releaseInflight(job.userId(), inflightToken(record));
            breaker.recordSuccess();

            log.info("Submission {} -> {}{}", submissionId, outcome.verdict(),
                    outcome.failedTest() != null ? " (test " + outcome.failedTest() + ")" : "");
        } catch (Judge0Exception e) {
            breaker.recordFailure();   // transport failure — may open the circuit; leave the record pending
            log.error("Judge0 unavailable for submission {}; leaving pending for reclaim (failures={})",
                    submissionId, breaker.failureCount(), e);
        } catch (Exception e) {
            log.error("Unexpected error judging submission {}; leaving pending", submissionId, e);
        }
    }

    /** Terminal failure for a redelivered-too-many-times record: write IE and clear it out. */
    public void poison(MapRecord<String, Object, Object> record) {
        Long submissionId = submissionId(record);
        if (submissionId == null) {
            ack(record);
            return;
        }
        JobRow job = store.loadJob(submissionId);
        if (job == null || "done".equals(job.status())) {
            ack(record);
            return;
        }
        store.writeVerdict(submissionId, new JudgeOutcome(Verdict.IE, null, 0, 0, null, null, null, List.of()));
        ack(record);
        publish(job.userId(), submissionId, Verdict.IE);
        releaseInflight(job.userId(), inflightToken(record));
        log.warn("Submission {} poisoned -> IE (exceeded max deliveries)", submissionId);
    }

    private Long submissionId(MapRecord<String, Object, Object> record) {
        Object v = record.getValue().get(FIELD_SUBMISSION_ID);
        if (v == null) {
            log.warn("Stream record {} has no {}, acking and skipping", record.getId(), FIELD_SUBMISSION_ID);
            return null;
        }
        return Long.parseLong(v.toString());
    }

    private void ack(MapRecord<String, Object, Object> record) {
        redis.opsForStream().acknowledge(props.streamKey(), props.group(), record.getId());
    }

    private void publish(long userId, long submissionId, Verdict verdict) {
        try {
            String payload = json.writeValueAsString(Map.of("submissionId", submissionId, "verdict", verdict.name()));
            redis.convertAndSend(props.userChannelPrefix() + userId, payload);
        } catch (Exception e) {
            log.warn("Failed to publish result for submission {}", submissionId, e);
        }
    }

    private String inflightToken(MapRecord<String, Object, Object> record) {
        Object v = record.getValue().get(FIELD_INFLIGHT_TOKEN);
        return v == null ? null : v.toString();
    }

    private String inflightKey(long userId) {
        return props.inflightKeyPrefix() + userId;
    }

    /**
     * P0-4: compare-and-delete so a slow submission can't free a newer one's lock. Records enqueued before
     * this change carry no token; for those we fall back to the old blind delete so a rolling upgrade is safe.
     */
    private void releaseInflight(long userId, String token) {
        if (token != null) {
            inflightLock.releaseIfOwner(inflightKey(userId), token);
        } else {
            redis.delete(inflightKey(userId));
        }
    }
}
