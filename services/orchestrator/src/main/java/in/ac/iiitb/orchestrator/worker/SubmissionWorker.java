package in.ac.iiitb.orchestrator.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * The live read loop. On start it ensures the consumer group exists on the subq stream (creating the
 * stream if needed), then a single background thread blocks on XREADGROUP and dispatches each new record
 * to the shared {@link JudgeExecutor} — the same {@link SubmissionProcessor} path the {@link Reclaimer}
 * uses for recovered jobs.
 *
 * Day 8 adds the circuit breaker: before each read the loop checks {@link Judge0CircuitBreaker#allowRequest()}.
 * While the breaker is OPEN the loop mostly sleeps, reading just a single record per open-window as a probe;
 * a successful probe closes the breaker and full-rate reading resumes.
 *
 * P0-1 (concurrency): judging is ~99% I/O wait on Judge0, so the Day-7 model — read a record, judge it to
 * completion, read the next — capped the whole platform at one submission at a time regardless of how much
 * Judge0 capacity sat behind it. The read is still single-threaded (one consumer, one offset), but each
 * record is now handed to the {@link JudgeExecutor} and judged on its own virtual thread, up to
 * {@code app.worker.judge-concurrency} at once.
 *
 * Backpressure is the {@link JudgeExecutor} handshake: the loop RESERVES slots before it reads, and asks
 * XREADGROUP for exactly that many records (COUNT = slots). So it never pulls more off the stream than it
 * can run — records it can't run would otherwise sit unacked in the PEL and look, to the reclaimer, like
 * work abandoned by a dead consumer. When all slots are busy, {@link JudgeExecutor#reserve(int)} blocks and
 * the loop naturally stops reading until a judge finishes.
 *
 * ACK / reclaim semantics are unchanged and still live inside {@link SubmissionProcessor#process}: a record
 * is acked only after a terminal outcome, and left pending on a Judge0 outage so the reclaimer revisits it.
 * Because the ack happens on the judging virtual thread (after the outcome), moving judging off the read
 * thread preserves that exactly. (One benign interaction: if the stream ever holds two records for the same
 * submission — a rejudge racing a reconciler — they can now be judged concurrently rather than back-to-back;
 * the {@code status <> 'done'} guard on the writeback keeps this correct, at the cost of one wasted judge.
 * A per-submission {@code SET NX} judging lock closes that window and pairs naturally with the in-flight
 * lock work in P0-4.)
 */
@Component
public class SubmissionWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SubmissionWorker.class);

    private final StringRedisTemplate redis;
    private final SubmissionProcessor processor;
    private final Judge0CircuitBreaker breaker;
    private final JudgeExecutor executor;
    private final WorkerProperties props;

    private volatile boolean running = false;
    private Thread thread;

    public SubmissionWorker(StringRedisTemplate redis, SubmissionProcessor processor,
                            Judge0CircuitBreaker breaker, JudgeExecutor executor, WorkerProperties props) {
        this.redis = redis;
        this.processor = processor;
        this.breaker = breaker;
        this.executor = executor;
        this.props = props;
    }

    @Override
    public void start() {
        ensureGroup();
        running = true;
        thread = new Thread(this::loop, "submission-worker");
        thread.setDaemon(false);   // keep the (non-web) JVM alive while the worker runs
        thread.start();
        log.info("Submission worker started: group={} consumer={} stream={} concurrency={}",
            props.group(), props.consumer(), props.streamKey(), executor.capacity());
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();     // wakes a reader blocked in reserve(); a blocked XREADGROUP unblocks on its BLOCK timeout
            try {
                thread.join(Duration.ofSeconds(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Drain in-flight judges while Redis/Postgres are still up (lifecycle stop runs before bean
        // destruction), so a judge finishing right now can still ACK and write its verdict. Idempotent —
        // Spring also calls close() on context shutdown.
        executor.close();
        log.info("Submission worker stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Stop early in the shutdown ordering so in-flight reads/writes can wind down cleanly. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void ensureGroup() {
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                connection.streamCommands().xGroupCreate(
                    props.streamKey().getBytes(),
                    props.group(),
                    ReadOffset.from("0"),    // read anything already queued before we existed
                    true);                   // MKSTREAM: create the stream if it isn't there yet
                return null;
            });
        } catch (Exception e) {
            // BUSYGROUP — the group already exists; that's fine.
            log.debug("Consumer group ensure: {}", e.getMessage());
        }
    }

    private void loop() {
        Consumer consumer = Consumer.from(props.group(), props.consumer());
        StreamOffset<String> offset = StreamOffset.create(props.streamKey(), ReadOffset.lastConsumed());

        while (running) {
            try {
                if (!breaker.allowRequest()) {
                    quietSleep(props.breakerPauseMs());   // circuit open — back off, don't hammer Judge0
                    continue;
                }

                // Reserve capacity FIRST, then read exactly that many — never pull more off the stream than
                // we can run. reserve() blocks when every judge slot is busy, which is the backpressure.
                int slots;
                try {
                    slots = executor.reserve(desiredReadCount());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;   // shutting down
                }

                List<MapRecord<String, Object, Object>> records;
                try {
                    StreamReadOptions options = StreamReadOptions.empty()
                        .count(slots)
                        .block(Duration.ofMillis(props.blockMs()));
                    records = redis.opsForStream().read(consumer, options, offset);
                } catch (RuntimeException readError) {
                    executor.releaseUnused(slots);   // read failed — hand the reserved slots back before retrying
                    throw readError;
                }

                if (records == null || records.isEmpty()) {
                    executor.releaseUnused(slots);   // BLOCK timed out, no work — release the reservation
                    continue;
                }
                dispatchBatch(records, slots);
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.error("Worker read loop error; backing off briefly for half second", e);
                quietSleep(500);
            }
        }
    }

    /** How many records to read this tick: a single probe while the breaker is open, else a full batch. */
    int desiredReadCount() {
        return breaker.isOpen() ? 1 : props.batchCount();
    }

    /**
     * Dispatch an already-read batch onto the judge pool, consuming the reserved slots. Each record is
     * judged on its own virtual thread via {@link SubmissionProcessor#process}; any reserved slots not
     * dispatched (a short read, or a batch cut off because the pool is shutting down) are returned.
     * Returns the number of records actually dispatched.
     */
    int dispatchBatch(List<MapRecord<String, Object, Object>> records, int reserved) {
        int dispatched = 0;
        boolean rejected = false;
        try {
            for (MapRecord<String, Object, Object> record : records) {
                if (dispatched >= reserved) {
                    break;   // defensive — read should never exceed the COUNT we reserved for
                }
                executor.dispatch(() -> processor.process(record));
                dispatched++;
            }
        } catch (RejectedExecutionException shuttingDown) {
            // Pool is closing; the rest of this batch stays pending in the PEL and the reclaimer recovers it.
            // The permit for the rejected record was already returned by JudgeExecutor.dispatch(), so it must
            // NOT be released again below.
            rejected = true;
            log.debug("judge pool rejected dispatch during shutdown; {} of {} dispatched",
                dispatched, records.size());
        } finally {
            // Return only the permits still held: reserved, minus those handed to running tasks, minus the
            // one a rejected dispatch already returned itself.
            int stillHeld = reserved - dispatched - (rejected ? 1 : 0);
            executor.releaseUnused(stillHeld);
        }
        return dispatched;
    }

    private void quietSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
