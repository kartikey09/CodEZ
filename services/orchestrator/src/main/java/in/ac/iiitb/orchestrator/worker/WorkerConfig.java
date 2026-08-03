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

    /**
     * The bounded pool that runs judges concurrently (P0-1). Spring auto-invokes its close() on shutdown
     * (AutoCloseable) to drain in-flight judges; the worker also closes it from lifecycle stop() while Redis
     * is still up, and close() is idempotent. When the reclaimer moves to this pool too (P1-5) it becomes
     * the single shared judging pool for both the live loop and recovery.
     */
    @Bean
    public JudgeExecutor judgeExecutor(WorkerProperties props) {
        return new JudgeExecutor(props.judgeConcurrency(), props.drainTimeoutMs());
    }
}
