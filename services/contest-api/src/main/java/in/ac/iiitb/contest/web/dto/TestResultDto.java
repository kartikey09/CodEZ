package in.ac.iiitb.contest.web.dto;

/**
 * One test's outcome in a Run's breakdown. {@code index} is a 1..N position re-enumerated at
 * read time (sorted by the real test_cases.ordinal, which is never itself returned) — this is
 * deliberate: since a Run only ever judges sample tests, this can never expose hidden-test data,
 * but re-indexing also stops any gap between sample ordinals from hinting at hidden-test layout.
 */
public record TestResultDto(
        int index,
        String verdict,
        Integer execTimeMs,
        Integer memoryKb) {
}
