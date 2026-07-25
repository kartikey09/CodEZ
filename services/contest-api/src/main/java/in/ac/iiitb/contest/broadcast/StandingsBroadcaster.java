package in.ac.iiitb.contest.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ac.iiitb.contest.scoring.ScoreboardService;
import in.ac.iiitb.contest.scoring.ScoringProperties;
import in.ac.iiitb.contest.scoring.StandingsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bridges Day-9 scoring to Day-10 push. When a contest's submissions change, this recomputes the board
 * (reusing the Day-9 ScoreboardService, which is the single source of the ICPC truth) and:
 *   - caches the JSON at standings:{id}:snapshot so a freshly-connected client gets the current board, and
 *   - publishes it on ch:standings:{id} for the realtime service to fan out.
 *
 * Calls are debounced per contest, so a flurry of verdicts collapses into one recompute + one broadcast.
 */
@Component
public class StandingsBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StandingsBroadcaster.class);

    private final ScoreboardService scoreboard;
    private final ScoringProperties scoringProps;
    private final BroadcastProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ScheduledExecutorService scheduler;

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    /** Wall-clock ms of the last broadcast per contest, so a sustained burst can't defer updates past maxWaitMs. */
    private final Map<Long, Long> lastPublishedAt = new ConcurrentHashMap<>();

    public StandingsBroadcaster(ScoreboardService scoreboard, ScoringProperties scoringProps,
                                BroadcastProperties props, StringRedisTemplate redis, ObjectMapper json,
                                ScheduledExecutorService scheduler) {
        this.scoreboard = scoreboard;
        this.scoringProps = scoringProps;
        this.props = props;
        this.redis = redis;
        this.json = json;
        this.scheduler = scheduler;
    }


    /**
     * Request a (debounced) recompute + broadcast for a contest. Normally we wait for a quiet period of
     * {@code debounceMs} before firing, so a flurry of verdicts collapses into one broadcast. But under a
     * sustained burst the quiet period would never arrive, so we cap the wait: if the board hasn't been
     * broadcast in the last {@code maxWaitMs}, the delay shrinks toward zero and an update is forced.
     */
    public void schedule(long contestId) {
        pending.compute(contestId, (cid, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            long now = System.currentTimeMillis();
            long lastPublish = lastPublishedAt.getOrDefault(cid, now);   // treat a never-seen contest as fresh
            long forceDeadline = lastPublish + props.maxWaitMs();
            long delay = Math.min(props.debounceMs(), Math.max(0L, forceDeadline - now));
            return scheduler.schedule(() -> publishNow(cid), delay, TimeUnit.MILLISECONDS);
        });
    }

    private void publishNow(long contestId) {
        lastPublishedAt.put(contestId, System.currentTimeMillis());          // reset the maxWaitMs clock
        try {
            scoreboard.rebuild(contestId);                                   // force a fresh ZSET from submissions
            StandingsResponse board = scoreboard.standings(contestId, props.broadcastLimit());
            String payload = json.writeValueAsString(board);
            redis.opsForValue().set(snapshotKey(contestId), payload, Duration.ofHours(12));
            redis.convertAndSend(props.standingsChannelPrefix() + contestId, payload);
        } catch (Exception e) {
            log.warn("standings broadcast failed for contest {}", contestId, e);
        } finally {
            pending.remove(contestId);
        }
    }

    private String snapshotKey(long contestId) {
        return scoringProps.keyPrefix() + contestId + ":snapshot";
    }
}
