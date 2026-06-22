package in.ac.iiitb.orchestrator.judge0;

/**
 * Judge0 status. The id is the stable contract we map to a verdict (Day 7):
 * 1 In Queue · 2 Processing · 3 Accepted · 4 Wrong Answer · 5 Time Limit Exceeded ·
 * 6 Compilation Error · 7-12 Runtime Error (various signals) · 13 Internal Error · 14 Exec Format Error.
 */
public record Judge0Status(Integer id, String description) {
}
