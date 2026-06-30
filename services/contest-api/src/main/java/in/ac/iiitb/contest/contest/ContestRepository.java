package in.ac.iiitb.contest.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    Optional<Contest> findFirstByStateOrderByStartsAtDesc(String state);
    Optional<Contest> findById(int id);
}
