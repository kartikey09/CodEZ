package in.ac.iiitb.auth.web.admin;

import in.ac.iiitb.auth.event.AuthEventRepository;
import in.ac.iiitb.auth.web.dto.AuthEventView;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the audit trail (Day 15). Under /auth/admin/** so the session filter's admin
 * gate applies. There is deliberately no delete or edit route — the trail is append-only, and the
 * one thing an attacker with an admin session would want is the ability to erase it.
 */
@RestController
@RequestMapping("/auth/admin/events")
public class AdminAuthEventController {

    private static final int MAX_LIMIT = 500;

    private final AuthEventRepository events;

    public AdminAuthEventController(AuthEventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<AuthEventView> list(@RequestParam(required = false) String loginId,
                                    @RequestParam(required = false) String event,
                                    @RequestParam(defaultValue = "100") int limit) {
        Pageable page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        List<in.ac.iiitb.auth.event.AuthEvent> rows;
        if (loginId != null && !loginId.isBlank()) {
            rows = events.findByLoginIdIgnoreCaseOrderByAtDesc(loginId.trim(), page);
        } else if (event != null && !event.isBlank()) {
            rows = events.findByEventOrderByAtDesc(event.trim().toUpperCase(java.util.Locale.ROOT), page);
        } else {
            rows = events.findAllByOrderByAtDesc(page);
        }
        return rows.stream().map(AuthEventView::of).toList();
    }
}
