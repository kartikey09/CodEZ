package in.ac.iiitb.contest.web.dto;

import java.time.Instant;

/**
 * Partial update of a contest's timing/state. Every field is optional — only the
 * non-null ones are applied, so the admin can nudge just the end time, or just flip
 * the state to "running", without resending the rest. Title is intentionally not
 * editable here (it's set at creation).
 */
public record UpdateContestRequest(
        Instant startsAt,
        Instant endsAt,
        String state) {
}
