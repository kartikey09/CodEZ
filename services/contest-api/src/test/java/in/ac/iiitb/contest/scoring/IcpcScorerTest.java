package in.ac.iiitb.contest.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class IcpcScorerTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final ScoringRules CLASSIC = new ScoringRules(20, true);     // CE counts

    /** An attempt on problem {@code pid} with verdict {@code v}, made {@code minute} minutes into the contest. */
    private static Attempt at(long pid, String v, int minute) {
        return new Attempt(pid, v, T0.plusSeconds(minute * 60L), minute);
    }

    @Test
    void noSubmissionsScoresZero() {
        UserScore s = IcpcScorer.score(1, List.of(), T0, CLASSIC);
        assertThat(s.solved()).isZero();
        assertThat(s.penalty()).isZero();
    }

    @Test
    void singleAcNoWrongs() {
        UserScore s = IcpcScorer.score(1, List.of(at(10, "AC", 30)), T0, CLASSIC);
        assertThat(s.solved()).isEqualTo(1);
        assertThat(s.penalty()).isEqualTo(30);                 // just the solve time
    }

    @Test
    void acWithTwoPriorWrongsAddsForty() {
        UserScore s = IcpcScorer.score(1, List.of(at(10, "WA", 5), at(10, "TLE", 20), at(10, "AC", 30)), T0, CLASSIC);
        assertThat(s.solved()).isEqualTo(1);
        assertThat(s.penalty()).isEqualTo(30 + 40);            // 30 + 20*2
        assertThat(s.perProblem().get(0).penaltyAttempts()).isEqualTo(2);
    }

    @Test
    void wrongsAfterTheAcDoNotCount() {
        UserScore s = IcpcScorer.score(1, List.of(at(10, "AC", 10), at(10, "WA", 40)), T0, CLASSIC);
        assertThat(s.penalty()).isEqualTo(10);
        assertThat(s.perProblem().get(0).penaltyAttempts()).isZero();
    }

    @Test
    void unsolvedProblemAddsNoPenalty() {
        UserScore s = IcpcScorer.score(1, List.of(at(10, "WA", 5), at(10, "RE", 9)), T0, CLASSIC);
        assertThat(s.solved()).isZero();
        assertThat(s.penalty()).isZero();                      // wrong attempts on an unsolved problem are free
    }

    @Test
    void internalErrorIsNeverAPenalty() {
        UserScore s = IcpcScorer.score(1, List.of(at(10, "IE", 3), at(10, "AC", 5)), T0, CLASSIC);
        assertThat(s.penalty()).isEqualTo(5);
        assertThat(s.perProblem().get(0).penaltyAttempts()).isZero();
    }

    @Test
    void compileErrorCountsOnlyWhenConfigured() {
        List<Attempt> subs = List.of(at(10, "CE", 4), at(10, "AC", 8));
        assertThat(IcpcScorer.score(1, subs, T0, new ScoringRules(20, true)).penalty()).isEqualTo(8 + 20);
        assertThat(IcpcScorer.score(1, subs, T0, new ScoringRules(20, false)).penalty()).isEqualTo(8);
    }

    @Test
    void totalsSumAcrossSolvedProblemsOnly() {
        UserScore s = IcpcScorer.score(1, List.of(
                at(10, "WA", 10), at(10, "AC", 20),   // solved: 20 + 20 = 40
                at(11, "WA", 5), at(11, "WA", 9)),    // unsolved: 0
                T0, CLASSIC);
        assertThat(s.solved()).isEqualTo(1);
        assertThat(s.penalty()).isEqualTo(40);
    }

    @Test
    void outOfOrderInputIsSortedByTime() {
        // AC arrives in the list before the earlier WA; the scorer must still see the WA as "before" the AC
        UserScore s = IcpcScorer.score(1, List.of(at(10, "AC", 30), at(10, "WA", 5)), T0, CLASSIC);
        assertThat(s.penalty()).isEqualTo(30 + 20);
    }
}
