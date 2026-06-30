package in.ac.iiitb.orchestrator.worker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Caches a problem's test cases keyed by problemId + test_data_version, so a burst of submissions
 * to the same problem doesn't re-query Postgres each time. Bumping a problem's test_data_version
 * (when an admin edits tests) yields a new key, so stale tests are never served. In-memory for v1;
 * the cache is rebuildable from Postgres at any time, so losing it on restart is harmless.
 */
@Component
public class TestCache {

    private final SubmissionStore store;
    private final Map<String, List<TestRow>> cache = new ConcurrentHashMap<>();

    public TestCache(SubmissionStore store) {
        this.store = store;
    }

    public List<TestRow> get(long problemId, int testDataVersion) {
        return cache.computeIfAbsent(problemId + ":" + testDataVersion, k -> store.loadTests(problemId));
    }
}
