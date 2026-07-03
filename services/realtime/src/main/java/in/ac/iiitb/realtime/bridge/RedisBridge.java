package in.ac.iiitb.realtime.bridge;

import java.nio.charset.StandardCharsets;

import in.ac.iiitb.realtime.config.RealtimeProperties;
import in.ac.iiitb.realtime.ws.SubscriberRegistry;
import in.ac.iiitb.realtime.ws.WsEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * The heart of the service: turn Redis pub/sub into WebSocket frames.
 *   ch:user:{userId}      -> the worker's verdict, forwarded to that user's tabs as {"type":"verdict",...}
 *   ch:standings:{contestId} -> contest-api's refreshed board, sent to that contest's watchers
 *
 * The channel name carries the id; the body is already JSON, so we forward it verbatim inside an envelope
 * without parsing it.
 */
@Component
public class RedisBridge implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisBridge.class);

    private final SubscriberRegistry registry;
    private final RealtimeProperties props;

    public RedisBridge(SubscriberRegistry registry, RealtimeProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        if (channel.startsWith(props.verdictChannelPrefix())) {
            Long userId = suffixId(channel, props.verdictChannelPrefix());
            if (userId != null) {
                WsEnvelope.broadcast(registry.forUser(userId), WsEnvelope.wrap("verdict", body));
            }
        } else if (channel.startsWith(props.standingsChannelPrefix())) {
            Long contestId = suffixId(channel, props.standingsChannelPrefix());
            if (contestId != null) {
                WsEnvelope.broadcast(registry.forContest(contestId), WsEnvelope.wrap("standings", body));
            }
        }
    }

    private static Long suffixId(String channel, String prefix) {
        try {
            return Long.valueOf(channel.substring(prefix.length()));
        } catch (NumberFormatException e) {
            log.debug("unparseable channel suffix: {}", channel);
            return null;
        }
    }
}
