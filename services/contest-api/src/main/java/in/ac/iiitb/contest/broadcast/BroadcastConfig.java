package in.ac.iiitb.contest.broadcast;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Adds the first pub/sub subscriber this service has (it previously only
 * used StringRedisTemplate for writes) plus a tiny daemon scheduler for debouncing board rebuilds. No new
 * Maven dependency: spring-data-redis is already present and the connection factory is Boot-autoconfigured.
 */
@Configuration
@EnableConfigurationProperties(BroadcastProperties.class)
public class BroadcastConfig {

    @Bean
    public RedisMessageListenerContainer broadcastListenerContainer(RedisConnectionFactory connectionFactory,
                                                                    VerdictListener verdictListener,
                                                                    BroadcastProperties props) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(verdictListener, new PatternTopic(props.verdictChannelPattern()));
        return container;
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService standingsScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "standings-broadcast");
            t.setDaemon(true);
            return t;
        });
    }
}
