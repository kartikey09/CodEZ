package in.ac.iiitb.contest.contest;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    Optional<Contest> findFirstByState(String state);
}
