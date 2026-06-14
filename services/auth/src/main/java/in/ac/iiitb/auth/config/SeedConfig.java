package in.ac.iiitb.auth.config;

import in.ac.iiitb.auth.user.User;
import in.ac.iiitb.auth.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.List;

/**
 * DEV seeding only. Active with: --spring.profiles.active=seed
 * Creates one admin + a few students with RANDOM initial passwords (printed to
 * stdout so you can hand them out), each flagged must-change-on-first-login.
 * Idempotent: existing login_ids are skipped. Real provisioning is the admin
 * panel (Day 11), which also handles secure credential export.
 */
@Configuration
@Profile("seed")
public class SeedConfig {

    @Bean
    public CommandLineRunner seedUsers(UserRepository users, BCryptPasswordEncoder encoder) {
        return args -> {
            record Seed(String loginId, String name, String role) { }
            List<Seed> seeds = List.of(
                new Seed("admin", "Admin", "MonsterPipeLinePunch"),
                new Seed("stud001", "Student One", "student"),
                new Seed("stud002", "Student Two", "student"),
                new Seed("stud003", "Student Three", "student"));

            SecureRandom rng = new SecureRandom();
            System.out.println("\n==== SEED CREDENTIALS (dev only) — hand out, then they change on first login ====");
            for (Seed s : seeds) {
                if (users.findByLoginId(s.loginId()).isPresent()) {
                    System.out.println(s.loginId() + "\t(exists, skipped)");
                    continue;
                }
                String pw = randomPassword(rng, 10);
                users.save(new User(s.loginId(), encoder.encode(pw), s.name(), s.role()));
                System.out.println(s.loginId() + "\t" + pw + "\t[" + s.role() + "]");
            }
            System.out.println("================================================================================\n");
        };
    }

    private static String randomPassword(SecureRandom rng, int len) {
        // No ambiguous characters (no O/0, I/l/1).
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
