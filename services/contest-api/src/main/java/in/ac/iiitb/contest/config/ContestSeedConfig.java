package in.ac.iiitb.contest.config;

import java.time.Duration;
import java.time.Instant;

import in.ac.iiitb.contest.contest.Contest;
import in.ac.iiitb.contest.contest.ContestRepository;
import in.ac.iiitb.contest.contest.Problem;
import in.ac.iiitb.contest.contest.ProblemRepository;
import in.ac.iiitb.contest.contest.TestCase;
import in.ac.iiitb.contest.contest.TestCaseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * DEV seeding only. Active with --spring.profiles.active=seed.
 * Creates one RUNNING contest (started an hour ago, ends in three hours) with two
 * problems, each having a visible sample test and a hidden test. Idempotent.
 * Real problem authoring is the admin panel (later milestone).
 */

@Configuration
@Profile("seed")
public class ContestSeedConfig {

    @Bean
    public CommandLineRunner seedContest(ContestRepository contests,
                                         ProblemRepository problems,
                                         TestCaseRepository tests) {
        return args -> {
            if (contests.findFirstByStateOrderByStartsAtDesc("running").isPresent()) {
                System.out.println("[seed] a running contest already exists — skipping");
                return;
            }
            Instant now = Instant.now();
            Contest c = contests.save(new Contest(
                "CodEZ Practice Round",
                now.minus(Duration.ofHours(1)),
                now.plus(Duration.ofHours(3)),
                "running"));

            Problem a = problems.save(new Problem(c.getId(), "A", "Sum of Two Numbers",
                "# Sum of Two Numbers\n\nRead two integers `a` and `b` on one line, print `a + b`.",
                1000, 256));
            tests.save(new TestCase(a.getId(), 1, "2 3\n", "5\n", true));        // sample
            tests.save(new TestCase(a.getId(), 2, "100 250\n", "350\n", false)); // hidden

            Problem b = problems.save(new Problem(c.getId(), "B", "Reverse a String",
                "# Reverse a String\n\nRead a string `s`, print it reversed.",
                1000, 256));
            tests.save(new TestCase(b.getId(), 1, "hello\n", "olleh\n", true));   // sample
            tests.save(new TestCase(b.getId(), 2, "codez\n", "zedoc\n", false));  // hidden

            System.out.println("[seed] created contest '" + c.getTitle()
                + "' (id=" + c.getId() + ") with problems A and B");
        };
    }
}
