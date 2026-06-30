package in.ac.iiitb.orchestrator.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Recovers submissions that a worker started but never acknowledged \u2014 the classic case being a worker
 * that was killed mid-judge (its record stays in the group's pending-entries list forever), but also any
 * job left pending by a Judge0 outage.
 *
 * Each sweep reads the group's PEL (XPENDING) and, for every pending entry:
 *   - if it has been delivered more than maxDeliveries times, it's a poison pill \u2014 claim it and write IE
 *     (no Judge0 involved), so one bad record can't wedge a consumer forever;
 *   - else if its last delivery is older than reclaimMinIdleMs (a dead/stuck owner), claim it (XCLAIM)
 *     and re-judge it through the same processor the live loop uses.
 *
 * While the circuit breaker is OPEN (Judge0 down) the reclaimer still poisons over-delivered entries but
 * does NOT re-judge \u2014 it leaves those for a later sweep so it isn't hammering a dead Judge0, and it lets
 * the read loop own the single probe.
 */
@Component
public class Reclaimer {

    private static final Logger log = LoggerFactory.getLogger(Reclaimer.class);

    private final StringRedisTemplate redis;
    private final SubmissionProcessor processor;
    private final Judge0CircuitBreaker breaker;
    private final WorkerProperties props;

    public Reclaimer(StringRedisTemplate redis, SubmissionProcessor processor,
                     Judge0CircuitBreaker breaker, WorkerProperties props) {
        this.redis = redis;
        this.processor = processor;
        this.breaker = breaker;
        this.props = props;
    }

    /** A record delivered more than the cap is a poison pill. */
    static boolean isPoison(long deliveryCount, int maxDeliveries) {
        return deliveryCount > maxDeliveries;
    }

    /** Only steal a job whose last delivery is older than the idle threshold (its owner looks dead). */
    static boolean shouldClaim(long idleMs, long minIdleMs) {
        return idleMs >= minIdleMs;
    }

    @Scheduled(fixedDelayString = "${app.worker.reclaim-interval-ms}",
               initialDelayString = "${app.worker.reclaim-interval-ms}")
    public void reclaim() {
        try {
            PendingMessages pending = redis.opsForStream()
                    .pending(props.streamKey(), props.group(), Range.unbounded(), props.reclaimBatch());
            if (pending == null || pending.isEmpty()) {
                return;
            }
            int poisoned = 0;
            int requeued = 0;
            for (PendingMessage p : pending) {
                RecordId id = p.getId();
                long deliveries = p.getTotalDeliveryCount();
                long idleMs = p.getElapsedTimeSinceLastDelivery().toMillis();

                if (isPoison(deliveries, props.maxDeliveries())) {
                            for (MapRecord<String, Object, Object> rec : claim(id, 0)) {
                                processor.poison(rec);
                                poisoned++;
                    }
                } else if (shouldClaim(idleMs, props.reclaimMinIdleMs())) {
                    if (breaker.isOpen()) {
                        continue;   // Judge0 down — don't re-judge now; a later sweep will
                    }
                    for (MapRecord<String, Object, Object> rec : claim(id, props.reclaimMinIdleMs())) {
                        processor.process(rec);
                        requeued++;
                    }
                }
            }
            if (poisoned > 0 || requeued > 0) {
                log.info("Reclaim sweep: requeued={} poisoned={}", requeued, poisoned);
            }
        } catch (Exception e) {
            // group may not exist yet (worker still starting), or a transient Redis blip — try again next sweep
            log.debug("Reclaim sweep skipped: {}", e.getMessage());
        }
    }

    /** XCLAIM the entry to this consumer; minIdle guards against racing another consumer. */
    private List<MapRecord<String, Object, Object>> claim(RecordId id, long minIdleMs) {
        return redis.opsForStream()
                .claim(props.streamKey(), props.group(), props.consumer(), Duration.ofMillis(minIdleMs), id);
    }
}
