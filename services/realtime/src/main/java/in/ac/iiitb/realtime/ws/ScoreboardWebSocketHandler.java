package in.ac.iiitb.realtime.ws;

import in.ac.iiitb.realtime.config.RealtimeProperties;
import in.ac.iiitb.realtime.session.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Per-connection lifecycle. On connect we wrap the raw session in a ConcurrentWebSocketSessionDecorator
 * (so the Redis listener thread can push frames safely while the client might also be sending), register it
 * under the user + contest from the handshake, and hand the client the current board immediately from the
 * snapshot key. The actual live frames are pushed by RedisBridge; this class only manages membership.
 */
@Component
public class ScoreboardWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScoreboardWebSocketHandler.class);
    private static final String ATTR_DECORATED = "decorated";

    private final SubscriberRegistry registry;
    private final StringRedisTemplate redis;
    private final RealtimeProperties props;

    public ScoreboardWebSocketHandler(SubscriberRegistry registry, StringRedisTemplate redis,
                                      RealtimeProperties props) {
        this.registry = registry;
        this.redis = redis;
        this.props = props;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Identity id = (Identity) session.getAttributes().get(HandshakeAuthInterceptor.ATTR_IDENTITY);
        Long contestId = (Long) session.getAttributes().get(HandshakeAuthInterceptor.ATTR_CONTEST);

        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, (int) props.sendTimeLimitMs(), props.sendBufferBytes());
        session.getAttributes().put(ATTR_DECORATED, safe);
        registry.add(id.userId(), contestId, safe);

        // hand over the current board, if contest-api has published one yet
        String snapshot = redis.opsForValue().get(props.snapshotKeyPrefix() + contestId + ":snapshot");
        if (snapshot != null) {
            safe.sendMessage(new TextMessage(WsEnvelope.wrap("standings", snapshot)));
        }
        log.debug("connected user={} contest={} session={}", id.userId(), contestId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // the protocol is server-push; the only client message we honour is a heartbeat
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Identity id = (Identity) session.getAttributes().get(HandshakeAuthInterceptor.ATTR_IDENTITY);
        Long contestId = (Long) session.getAttributes().get(HandshakeAuthInterceptor.ATTR_CONTEST);
        WebSocketSession safe = (WebSocketSession) session.getAttributes().get(ATTR_DECORATED);
        if (id != null && contestId != null && safe != null) {
            registry.remove(id.userId(), contestId, safe);
        }
    }
}
