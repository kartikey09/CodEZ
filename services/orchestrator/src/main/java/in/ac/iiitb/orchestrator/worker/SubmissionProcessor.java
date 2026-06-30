package in.ac.iiitb.orchestrator.worker;

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
 * The only stream field read is {@code submissionId}; everything else comes from Postgres.
 */
@Component
public class SubmissionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SubmissionProcessor.class);
    static final String FIELD_SUBMISSION_ID = "submissionId";

    private final StringRedisTemplate redis;
    private final SubmissionStore store;
    private final TestCache tests;
    private final JudgeService judge;
    private final Judge0CircuitBreaker breaker;
    private final WorkerProperties props;
    private final ObjectMapper json;

    public SubmissionProcessor(StringRedisTemplate redis, SubmissionStore store, TestCache tests,
                               JudgeService judge, Judge0CircuitBreaker breaker, WorkerProperties props,
                               ObjectMapper json) {
        this.redis = redis;
        this.store = store;
        this.tests = tests;
        this.judge = judge;
        this.breaker = breaker;
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
            ProblemRow problem = store.loadProblem(job.problemId());
            JudgeOutcome outcome = judge.judge(job, problem, tests.get(job.problemId(), problem.testDataVersion()));
            store.writeVerdict(submissionId, outcome);

            ack(record);
            publish(job.userId(), submissionId, outcome.verdict());
            releaseInflight(job.userId());
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
        store.writeVerdict(submissionId, new JudgeOutcome(Verdict.IE, null, null, null, null));
        ack(record);
        publish(job.userId(), submissionId, Verdict.IE);
        releaseInflight(job.userId());
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

    private void releaseInflight(long userId) {
        redis.delete(props.inflightKeyPrefix() + userId);
    }
}
