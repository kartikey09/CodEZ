package in.ac.iiitb.orchestrator.worker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(WorkerProperties.class)
@EnableScheduling                       // drives the Reclaimer's @Scheduled sweep
public class WorkerConfig {

    /** One breaker shared by the read loop and the reclaimer; wall-clock in prod, fakeable in tests. */
    @Bean
    public Judge0CircuitBreaker judge0CircuitBreaker(WorkerProperties props) {
        return new Judge0CircuitBreaker(props.breakerFailureThreshold(), props.breakerOpenMs(),
            System::currentTimeMillis);
    }
}
