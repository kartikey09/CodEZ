package in.ac.iiitb.realtime.config;

import in.ac.iiitb.realtime.ws.HandshakeAuthInterceptor;
import in.ac.iiitb.realtime.ws.ScoreboardWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Exposes the single endpoint clients connect to:  ws://host:8083/ws/scoreboard?contestId={id}
 * The handshake interceptor authenticates via the sid cookie before the upgrade completes.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ScoreboardWebSocketHandler handler;
    private final HandshakeAuthInterceptor authInterceptor;
    private final RealtimeProperties props;

    public WebSocketConfig(ScoreboardWebSocketHandler handler, HandshakeAuthInterceptor authInterceptor,
                           RealtimeProperties props) {
        this.handler = handler;
        this.authInterceptor = authInterceptor;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/scoreboard")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns(props.allowedOriginPatterns().toArray(new String[0]));
    }
}
