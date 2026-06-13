# Scoring rules (decided Day 1 — frozen)

ICPC-style. The leaderboard ranks by:
1. **Problems solved** — descending.
2. **Total penalty (minutes)** — ascending (tie-breaker).

## Penalty
For each **solved** problem, penalty contribution =
`minutes_from_contest_start_to_first_AC  +  20 * (wrong_attempts_before_that_AC)`

- Wrong attempts = verdicts of WA / TLE / MLE / RE submitted **before** the first AC on that problem.
- **Compilation Error (CE) costs nothing** — not counted as a wrong attempt, no penalty.
- Submissions **after** a problem is already solved do not change the score.
- Unsolved problems contribute **zero** penalty (only solved problems accrue penalty).

## Redis encoding (implemented Day 9)
Single sortable score per user in a ZSET `lb:{contestId}`:

```
score = (solved * 1_000_000) - penaltyMinutes
```

Read top-N with `ZREVRANGE`; a user's own rank with `ZREVRANK`. Per-user cell
state (first-AC time, attempt counts per problem) lives in hash `lbu:{contestId}:{userId}`.

## Verdicts
`AC` Accepted · `WA` Wrong Answer · `TLE` Time Limit Exceeded ·
`MLE` Memory Limit Exceeded · `RE` Runtime Error · `CE` Compilation Error ·
`IE` Internal Error (judge fault — never penalised, always re-judgeable).

## Time
Contest window is **server-authoritative**. The countdown is driven by
`GET /api/time` (server clock), never the browser clock. A small configurable
grace (a few seconds past `ends_at`) absorbs network jitter.
