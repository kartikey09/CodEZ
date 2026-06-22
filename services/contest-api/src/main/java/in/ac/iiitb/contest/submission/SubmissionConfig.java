package in.ac.iiitb.contest.submission;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SubmissionProperties.class)
public class SubmissionConfig {
}
