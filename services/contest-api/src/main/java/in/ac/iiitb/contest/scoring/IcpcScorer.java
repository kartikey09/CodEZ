package in.ac.iiitb.contest.scoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ICPC scoring for one user, computed fresh from their submissions (the {@code submissions} table is the
 * source of truth; nothing here is incremental, so it's always correct and trivially re-runnable).
 *
 * The rules:
 *   - A problem is SOLVED at its first AC. Its time is minutes from contest start to that first AC.
 *   - Its penalty = AC-minute + penaltyPerWrong x (rejected attempts made BEFORE the first AC).
 *     Attempts after the AC don't count; IE never counts; CE counts only if configured.
 *   - An unsolved problem contributes 0 penalty no matter how many wrong attempts it has.
 *   - Totals: solved = number of solved problems; penalty = sum over solved problems.
 *
 * Ranking (done by the caller) is solved DESC, then penalty ASC.
 */
public final class IcpcScorer {

    private IcpcScorer() {
    }

    public static UserScore score(long userId, List<Attempt> attempts, Instant contestStart, ScoringRules rules) {
        // group by problem, each group in submission-time order
        List<Attempt> ordered = new ArrayList<>(attempts);
        ordered.sort(Comparator.comparing(Attempt::at).thenComparingLong(Attempt::tieBreak));
        Map<Long, List<Attempt>> byProblem = new LinkedHashMap<>();
        for (Attempt a : ordered) {
            byProblem.computeIfAbsent(a.problemId(), k -> new ArrayList<>()).add(a);
        }

        int solvedCount = 0;
        long totalPenalty = 0;
        long lastAcMs = 0;
        List<UserScore.Problem> perProblem = new ArrayList<>();

        for (Map.Entry<Long, List<Attempt>> entry : byProblem.entrySet()) {
            long problemId = entry.getKey();
            List<Attempt> subs = entry.getValue();

            int firstAcIdx = -1;
            for (int i = 0; i < subs.size(); i++) {
                if ("AC".equals(subs.get(i).verdict())) {
                    firstAcIdx = i;
                    break;
                }
            }

            if (firstAcIdx >= 0) {
                Attempt ac = subs.get(firstAcIdx);
                long minute = Math.max(0, Duration.between(contestStart, ac.at()).toMinutes());
                int penaltyAttempts = 0;
                for (int i = 0; i < firstAcIdx; i++) {
                    if (rules.isRejection(subs.get(i).verdict())) {
                        penaltyAttempts++;
                    }
                }
                solvedCount++;
                totalPenalty += minute + (long) rules.penaltyPerWrong() * penaltyAttempts;
                lastAcMs = Math.max(lastAcMs, ac.at().toEpochMilli());
                perProblem.add(new UserScore.Problem(problemId, true, penaltyAttempts, (int) minute));
            } else {
                int rejections = 0;
                for (Attempt a : subs) {
                    if (rules.isRejection(a.verdict())) {
                        rejections++;
                    }
                }
                perProblem.add(new UserScore.Problem(problemId, false, rejections, null));
            }
        }

        return new UserScore(userId, solvedCount, totalPenalty, lastAcMs, perProblem);
    }
}
