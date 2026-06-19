package in.ac.iiitb.contest.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Postgres + one Redis shared by every @SpringBootTest in the suite
 * (the "singleton container" pattern). Started once in a static block and left
 * running — Testcontainers' Ryuk reaps them when the JVM exits. Because every IT
 * sees identical properties, Spring caches and reuses a single application context,
 * so the whole integration suite pays for one boot, not one-per-class.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
