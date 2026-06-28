package in.ac.iiitb.orchestrator.worker;

/**
 * Judge0 status id -> our verdict. The id is Judge0's stable contract:
 *   3 AC · 4 WA · 5 TLE · 6 CE · 7..12 Runtime Error (various signals) · 13/14 Internal/Exec-format.
 *
 * MLE decision (documented once, here): Judge0 CE has no dedicated out-of-memory status, so a
 * memory blow-up surfaces as a Runtime Error. We relabel an RE as MLE only when the reported
 * memory sits at (or above) the problem's limit; otherwise it stays RE. Ids 1/2 (In Queue /
 * Processing) should never reach this method — the worker polls a token to completion first —
 * so they fall through to IE defensively.
 */
public final class VerdictMapper {

    private VerdictMapper() {
    }

    public static Verdict map(int statusId, Integer memoryKb, int memoryLimitKb) {
        return switch (statusId) {
            case 3 -> Verdict.AC;
            case 4 -> Verdict.WA;
            case 5 -> Verdict.TLE;
            case 6 -> Verdict.CE;
            case 7, 8, 9, 10, 11, 12 -> runtimeOrMemory(memoryKb, memoryLimitKb);
            case 13, 14 -> Verdict.IE;
            default -> Verdict.IE;
        };
    }

    private static Verdict runtimeOrMemory(Integer memoryKb, int memoryLimitKb) {
        if (memoryKb != null && memoryLimitKb > 0 && memoryKb >= memoryLimitKb) {
            return Verdict.MLE;
        }
        return Verdict.RE;
    }
}
