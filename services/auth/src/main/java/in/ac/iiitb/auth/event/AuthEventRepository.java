package in.ac.iiitb.auth.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

    List<AuthEvent> findAllByOrderByAtDesc(Pageable page);

    List<AuthEvent> findByLoginIdIgnoreCaseOrderByAtDesc(String loginId, Pageable page);

    List<AuthEvent> findByEventOrderByAtDesc(String event, Pageable page);
}
