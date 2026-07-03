package in.ac.iiitb.realtime.config;

import java.util.List;

import in.ac.iiitb.realtime.bridge.RedisBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

/**
 * Wires the single pub/sub subscriber that feeds the bridge. The connection factory is Boot-autoconfigured
 * from spring.data.redis.
 */
@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       RedisBridge bridge,
                                                                       RealtimeProperties props) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        List<Topic> topics = List.of(
                new PatternTopic(props.verdictChannelPattern()),
                new PatternTopic(props.standingsChannelPattern()));
        container.addMessageListener(bridge, topics);
        return container;
    }
}
