package in.ac.iiitb.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Day 6 ships only the Judge0 client + its config; the stream-consuming worker loop
 * lands on Day 7. Started as a non-web application (see spring.main.web-application-type).
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
