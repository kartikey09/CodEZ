package in.ac.iiitb.contest.contest;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "state", nullable = false)
    private String state = "draft";

    protected Contest() {
    }

    public Contest(String title, Instant startsAt, Instant endsAt, String state) {
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.state = state;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
}
