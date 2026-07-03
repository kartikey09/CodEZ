package in.ac.iiitb.contest.scoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.error.NotFoundException;
import in.ac.iiitb.contest.submission.Submission;
import in.ac.iiitb.contest.submission.SubmissionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Maintains the live ICPC leaderboard for a contest.
 *
 * The board is DERIVED from the {@code submissions} table (the source of truth the worker writes) and
 * CACHED in a Redis sorted set per contest, so reads are O(top-N) and the structure is exactly what the
 * realtime push (Day 10) will broadcast. It's never authoritative state — losing Redis just costs one
 * recompute. The composite ZSET score encodes the ICPC order in a single number:
 *
 *     score = solved x 100_000_000 - penalty
 *
 * which (since a contestant's penalty is far below 100M) sorts by solved DESC, then penalty ASC under
 * ZREVRANGE. Per-user breakdown (the cells) is stored alongside in a hash so the endpoint needn't recompute.
 *
 * Freshness: a request rebuilds the board only if the cache flag has expired (cacheTtlMs), so a burst of
 * polls costs one recompute, not one per request. Admins can force a rebuild.
 */
@Service
public class ScoreboardService {

    private static final double SOLVED_WEIGHT = 100_000_000d;   // larger than any achievable penalty

    private final ContestRepository contests;
    private final ProblemRepository problems;
    private final SubmissionRepository submissions;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ScoringProperties props;

    public ScoreboardService(ContestRepository contests, ProblemRepository problems,
                             SubmissionRepository submissions, StringRedisTemplate redis,
                             JdbcTemplate jdbc, ObjectMapper json, ScoringProperties props) {
        this.contests = contests;
        this.problems = problems;
        this.submissions = submissions;
        this.redis = redis;
        this.jdbc = jdbc;
        this.json = json;
        this.props = props;
    }

    private String zKey(long contestId)      { return props.keyPrefix() + contestId; }
    private String detailKey(long contestId) { return props.keyPrefix() + contestId + ":detail"; }
    private String freshKey(long contestId)  { return props.keyPrefix() + contestId + ":fresh"; }

    /** The top {@code limit} of the board, rebuilt from submissions if the cache has expired. */
    public StandingsResponse standings(long contestId, int limit) {
        contests.findById(contestId).orElseThrow(NotFoundException::new);
        ensureFresh(contestId);

        List<String> labels = problems.findByContestIdOrderByLabel(contestId).stream()
            .map(Problem::getLabel).toList();

        Set<TypedTuple<String>> top = redis.opsForZSet()
            .reverseRangeWithScores(zKey(contestId), 0, limit - 1L);

        List<StandingsResponse.Row> rows = new ArrayList<>();
        if (top != null && !top.isEmpty()) {
            List<Long> ids = top.stream().map(t -> Long.parseLong(t.getValue())).toList();
            Map<Long, String> names = displayNames(ids);

            int rank = 0;
            int index = 0;
            Double prevScore = null;
            for (TypedTuple<String> t : top) {
                long userId = Long.parseLong(t.getValue());
                double score = t.getScore() == null ? 0 : t.getScore();
                index++;
                if (prevScore == null || score != prevScore) {   // competition ranking: ties share, then skip
                    rank = index;
                    prevScore = score;
                }
                UserDetail d = readDetail(contestId, userId);
                rows.add(new StandingsResponse.Row(rank, userId,
                    names.getOrDefault(userId, "user#" + userId), d.solved(), d.penalty(), d.cells()));
            }
        }
        return new StandingsResponse(contestId, labels, rows, Instant.now());
    }

    /** The caller's own row and competition rank. */
    public MyStanding myStanding(long contestId, long userId) {
        contests.findById(contestId).orElseThrow(NotFoundException::new);
        ensureFresh(contestId);

        Double score = redis.opsForZSet().score(zKey(contestId), Long.toString(userId));
        if (score == null) {
            return new MyStanding(contestId, userId, null, 0, 0, List.of());   // nothing judged yet
        }
        // rank = 1 + (users with a strictly greater composite score). Scores are integer-valued, so +0.5 excludes ties.
        Long strictlyBetter = redis.opsForZSet()
            .count(zKey(contestId), score + 0.5, Double.POSITIVE_INFINITY);
        UserDetail d = readDetail(contestId, userId);
        int rank = (int) ((strictlyBetter == null ? 0 : strictlyBetter) + 1);
        return new MyStanding(contestId, userId, rank, d.solved(), d.penalty(), d.cells());
    }

    /** Recompute the whole board from the submissions table. Returns how many contestants were scored. */
    public int rebuild(long contestId) {
        Contest contest = contests.findById(contestId).orElseThrow(NotFoundException::new);
        List<Problem> probs = problems.findByContestIdOrderByLabel(contestId);
        List<Submission> done = submissions.findByContestIdAndStatusOrderByCreatedAtAsc(contestId, "done");

        Map<Long, List<Attempt>> byUser = new LinkedHashMap<>();
        for (Submission s : done) {
            byUser.computeIfAbsent(s.getUserId(), k -> new ArrayList<>())
                .add(new Attempt(s.getProblemId(), s.getVerdict(), s.getCreatedAt(), s.getId()));
        }

        String zk = zKey(contestId);
        String dk = detailKey(contestId);
        redis.delete(zk);
        redis.delete(dk);

        ScoringRules rules = props.rules();
        for (Map.Entry<Long, List<Attempt>> e : byUser.entrySet()) {
            UserScore us = IcpcScorer.score(e.getKey(), e.getValue(), contest.getStartsAt(), rules);
            double composite = us.solved() * SOLVED_WEIGHT - us.penalty();
            redis.opsForZSet().add(zk, Long.toString(us.userId()), composite);
            redis.opsForHash().put(dk, Long.toString(us.userId()), writeDetail(us, probs));
        }
        markFresh(contestId);
        return byUser.size();
    }

    // ---- internals ----

    private void ensureFresh(long contestId) {
        if (!Boolean.TRUE.equals(redis.hasKey(freshKey(contestId)))) {
            rebuild(contestId);
        }
    }

    private void markFresh(long contestId) {
        redis.opsForValue().set(freshKey(contestId), "1", Duration.ofMillis(Math.max(250L, props.cacheTtlMs())));
    }

    /** Per-user payload stored in the detail hash so the endpoint doesn't recompute cells on read. */
    record UserDetail(int solved, long penalty, List<StandingsResponse.Cell> cells) {
    }

    private String writeDetail(UserScore us, List<Problem> probs) {
        Map<Long, UserScore.Problem> byPid = new HashMap<>();
        for (UserScore.Problem p : us.perProblem()) {
            byPid.put(p.problemId(), p);
        }
        List<StandingsResponse.Cell> cells = new ArrayList<>();
        for (Problem prob : probs) {
            UserScore.Problem pr = byPid.get(prob.getId());
            if (pr == null) {
                cells.add(new StandingsResponse.Cell(prob.getLabel(), "none", 0, null));
            } else if (pr.solved()) {
                cells.add(new StandingsResponse.Cell(prob.getLabel(), "solved", pr.penaltyAttempts(), pr.acMinute()));
            } else {
                cells.add(new StandingsResponse.Cell(prob.getLabel(),
                    pr.penaltyAttempts() > 0 ? "attempted" : "none", pr.penaltyAttempts(), null));
            }
        }
        try {
            return json.writeValueAsString(new UserDetail(us.solved(), us.penalty(), cells));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise standings detail", e);
        }
    }

    private UserDetail readDetail(long contestId, long userId) {
        Object v = redis.opsForHash().get(detailKey(contestId), Long.toString(userId));
        if (v == null) {
            return new UserDetail(0, 0, List.of());
        }
        try {
            return json.readValue(v.toString(), UserDetail.class);
        } catch (Exception e) {
            return new UserDetail(0, 0, List.of());
        }
    }

    /** Cross-reads display names from the shared users table (auth owns writes; this is a read-only lookup). */
    private Map<Long, String> displayNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, String> out = new HashMap<>();
        jdbc.query("SELECT id, display_name FROM users WHERE id IN (" + placeholders + ")",
            rs -> { out.put(rs.getLong("id"), rs.getString("display_name")); },
            ids.toArray());
        return out;
    }
}
