package in.ac.iiitb.contest.contest;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A contest-wide notice shown to students as a banner. Retracting a notice flips
 * {@code active} to false (soft delete) rather than removing the row.
 */
@Entity
@Table(name = "announcements")
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Announcement() {
        // for JPA
    }

    public Announcement(Long contestId, String message) {
        this.contestId = contestId;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getContestId() { return contestId; }
    public String getMessage() { return message; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
