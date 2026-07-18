-- Day 13: contest-wide announcements shown as a banner to students.
-- Soft-deleted (active flag) rather than hard-deleted so the admin can retract a
-- notice without losing the record. Scoped to a contest; the student endpoint only
-- ever returns active rows for the currently running contest.

CREATE TABLE announcements (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contest_id BIGINT      NOT NULL REFERENCES contests(id),
    message    TEXT        NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_announcements_contest_active
    ON announcements (contest_id, active, created_at DESC);
