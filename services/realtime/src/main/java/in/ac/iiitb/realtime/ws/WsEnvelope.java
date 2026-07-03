package in.ac.iiitb.realtime.ws;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Helpers so the handler and the Redis bridge agree on the wire format. realtime never parses the payloads
 * it forwards — they're already JSON from the worker / contest-api — it just wraps them in a typed envelope:
 *
 *     {"type":"verdict","data": <the worker's payload> }
 *     {"type":"standings","data": <contest-api's board> }
 */
public final class WsEnvelope {

    private static final Logger log = LoggerFactory.getLogger(WsEnvelope.class);

    private WsEnvelope() {
    }

    /** Splice a raw JSON value into a typed envelope without re-parsing it. */
    public static String wrap(String type, String rawJson) {
        return "{\"type\":\"" + type + "\",\"data\":" + rawJson + "}";
    }

    /** Send one text frame to every open session, serialising per session (decorators handle concurrency). */
    public static void broadcast(Set<WebSocketSession> sessions, String text) {
        if (sessions.isEmpty()) {
            return;
        }
        TextMessage frame = new TextMessage(text);
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) {
                continue;
            }
            try {
                s.sendMessage(frame);
            } catch (IOException e) {
                log.debug("drop frame to session {}: {}", s.getId(), e.toString());
            }
        }
    }
}
