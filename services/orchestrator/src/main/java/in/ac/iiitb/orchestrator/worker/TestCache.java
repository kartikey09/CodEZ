package in.ac.iiitb.orchestrator.worker;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caches a problem's test cases keyed by problemId + test_data_version, so a burst of submissions
 * to the same problem doesn't re-query Postgres each time. Bumping a problem's test_data_version
 * (when an admin edits tests) yields a new key, so stale tests are never served. In-memory for v1;
 * the cache is rebuildable from Postgres at any time, so losing it on restart is harmless.
 *
 * <p>Bounded by app.worker.test-cache-max-entries entries (one entry = one problem's tests at one
 * version) and by app.worker.test-cache-expire-after-access-ms since the last read, so a long-lived
 * worker can't accumulate every problem it has ever judged. Over capacity, Caffeine evicts by
 * recency/frequency (W-TinyLFU): like LRU it drops cold entries first, but it also keeps a hot
 * problem resident when a scan of one-off problems would have flushed it out of a strict LRU.
 */
@Component
public class TestCache {

    private final SubmissionStore store;
    private final Cache<String, List<TestRow>> cache;

    public TestCache(SubmissionStore store, WorkerProperties props) {
        this.store = store;
        this.cache = Caffeine.newBuilder()
            .maximumSize(props.testCacheMaxEntries())
            .expireAfterAccess(Duration.ofMillis(props.testCacheExpireAfterAccessMs()))
            .build();
    }

    public List<TestRow> get(long problemId, int testDataVersion) {
        return cache.get(problemId + ":" + testDataVersion, k -> store.loadTests(problemId));
    }
}
