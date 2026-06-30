package in.ac.iiitb.orchestrator.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The live read loop. On start it ensures the consumer group exists on the subq stream (creating the
 * stream if needed), then a single background thread blocks on XREADGROUP and hands each new record to
 * the shared {@link SubmissionProcessor} \u2014 the same path the {@link Reclaimer} uses for recovered jobs.
 *
 * Day 8 adds the circuit breaker: before each read the loop checks {@link Judge0CircuitBreaker#allowRequest()}.
 * While the breaker is OPEN the loop mostly sleeps, reading just a single record per open-window as a probe;
 * a successful probe closes the breaker and full-rate reading resumes. Records already delivered before the
 * outage stay pending and are recovered by the reclaimer.
 */
@Component
public class SubmissionWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SubmissionWorker.class);

    private final StringRedisTemplate redis;
    private final SubmissionProcessor processor;
    private final Judge0CircuitBreaker breaker;
    private final WorkerProperties props;

    private volatile boolean running = false;
    private Thread thread;

    public SubmissionWorker(StringRedisTemplate redis, SubmissionProcessor processor,
                            Judge0CircuitBreaker breaker, WorkerProperties props) {
        this.redis = redis;
        this.processor = processor;
        this.breaker = breaker;
        this.props = props;
    }

    @Override
    public void start() {
        ensureGroup();
        running = true;
        thread = new Thread(this::loop, "submission-worker");
        thread.setDaemon(false);   // keep the (non-web) JVM alive while the worker runs
        thread.start();
        log.info("Submission worker started: group={} consumer={} stream={}",
            props.group(), props.consumer(), props.streamKey());
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
                    quietSleep(props.breakerPauseMs());   //    1s, circuit open — back off, don't hammer Judge0
                    continue;
                }
                // while open, the allowed request is a single-record probe; otherwise read a full batch
                int count = breaker.isOpen() ? 1 : props.batchCount();
                StreamReadOptions options = StreamReadOptions.empty()
                    .count(count)
                    .block(Duration.ofMillis(props.blockMs()));

                List<MapRecord<String, Object, Object>> records =
                    redis.opsForStream().read(consumer, options, offset);
                if (records == null || records.isEmpty()) {
                    continue;   // BLOCK timed out — loop and read again
                }
                for (MapRecord<String, Object, Object> record : records) {
                    processor.process(record);
                }
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.error("Worker read loop error; backing off briefly for half second", e);
                quietSleep(500);
            }
        }
    }

    private void quietSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
