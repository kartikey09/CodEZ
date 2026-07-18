package in.ac.iiitb.auth.web.dto;

import java.util.List;

/**
 * Outcome of a roster import. {@code created} carries each new account with its
 * one-time password; {@code skipped} explains every row that was not created (duplicate
 * login id, blank field, bad role) — the import is best-effort and never aborts the
 * whole batch for one bad line.
 */
public record ImportResult(
        List<CreatedUser> created,
        List<Skipped> skipped) {

    public record Skipped(
        int line,
        String loginId,
        String reason) {
    }
}
