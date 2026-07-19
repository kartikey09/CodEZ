package in.ac.iiitb.auth.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One row of the audit trail (Flyway V2). Append-only: there are no setters by design. */
@Entity
@Table(name = "auth_events")
public class AuthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", insertable = false, updatable = false)
    private Instant at;

    @Column(name = "event", nullable = false)
    private String event;

    @Column(name = "login_id")
    private String loginId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "detail")
    private String detail;

    protected AuthEvent() {
        // for JPA
    }

    public AuthEvent(AuthEventType event, String loginId, Long userId, Long actorId,
                     String ip, String userAgent, String detail) {
        this.event = event.name();
        this.loginId = loginId;
        this.userId = userId;
        this.actorId = actorId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getEvent() { return event; }
    public String getLoginId() { return loginId; }
    public Long getUserId() { return userId; }
    public Long getActorId() { return actorId; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getDetail() { return detail; }
}
