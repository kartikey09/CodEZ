package in.ac.iiitb.contest.web.dto;

import java.time.Instant;

/**
 * Everything the frontend shell needs in one round trip: which contest is live (its id names the
 * WebSocket channel and the standings path), the window for the countdown, and the server's clock so the
 * client can compute skew (countdowns are computed against server time, never the browser's clock).
 */
public record ContestInfo(
        long id,
        String title,
        Instant startsAt,
        Instant endsAt,
        String state,
        long serverEpochMillis) {
}
