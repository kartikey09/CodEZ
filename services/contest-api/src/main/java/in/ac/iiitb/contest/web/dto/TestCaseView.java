package in.ac.iiitb.contest.web.dto;

/**
 * A test case as shown in the authoring UI.
 *
 * Hidden-test data never leaves the server: {@code input} and {@code expectedOutput} are populated
 * ONLY for samples (which students already see). For hidden tests both are null and the admin gets
 * sizes instead — enough to spot a truncated or empty upload without turning the admin session into
 * a way to read the answer key. Editing a hidden test is delete + re-add.
 */
public record TestCaseView(
        long id,
        int ordinal,
        boolean sample,
        int inputBytes,
        int expectedOutputBytes,
        String input,
        String expectedOutput) {
}
