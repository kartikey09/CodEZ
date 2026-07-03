package in.ac.iiitb.realtime.ws;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Who is listening to what. Two indexes:
 *   - by contest, so a refreshed board fans out to everyone watching that contest;
 *   - by user, so a verdict reaches that user's open tabs.
 * Sessions stored here are the concurrency-safe decorators created by the handler.
 */
@Component
public class SubscriberRegistry {

    private final Map<Long, Set<WebSocketSession>> byContest = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();

    public void add(long userId, long contestId, WebSocketSession session) {
        byContest.computeIfAbsent(contestId, k -> ConcurrentHashMap.newKeySet()).add(session);
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(long userId, long contestId, WebSocketSession session) {
        Set<WebSocketSession> c = byContest.get(contestId);
        if (c != null) {
            c.remove(session);
            if (c.isEmpty()) {
                byContest.remove(contestId, c);
            }
        }
        Set<WebSocketSession> u = byUser.get(userId);
        if (u != null) {
            u.remove(session);
            if (u.isEmpty()) {
                byUser.remove(userId, u);
            }
        }
    }

    public Set<WebSocketSession> forContest(long contestId) {
        return byContest.getOrDefault(contestId, Set.of());
    }

    public Set<WebSocketSession> forUser(long userId) {
        return byUser.getOrDefault(userId, Set.of());
    }
}
