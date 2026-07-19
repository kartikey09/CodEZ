package in.ac.iiitb.contest.web.dto;

import java.util.List;

/** Everything the authoring UI needs for one problem, including its test-case inventory. */
public record ProblemAdminDetail(
        long id,
        long contestId,
        String label,
        String title,
        String statementMd,
        int timeLimitMs,
        int memoryLimitMb,
        int testDataVersion,
        long testCount,
        long pendingSubmissions,
        List<TestCaseView> tests) {
}
