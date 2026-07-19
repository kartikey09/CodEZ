-- Day 15: an append-only audit trail for anything security-relevant in auth-service.
--
-- login_id is recorded as SUBMITTED (not resolved), so failed attempts against accounts that
-- don't exist are still visible — that pattern is exactly what a spray looks like. user_id is
-- filled only when the account resolved, and carries no FK so a future user deletion can't
-- silently erase the trail.
--
-- Passwords, hashes and session ids are NEVER written here.

CREATE TABLE auth_events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    event      TEXT        NOT NULL,   -- LOGIN_SUCCESS | LOGIN_FAILED | LOGIN_LOCKED | ...
    login_id   TEXT,                   -- as submitted
    user_id    BIGINT,                 -- resolved account, when there was one
    actor_id   BIGINT,                 -- the admin who performed an administrative action
    ip         TEXT,
    user_agent TEXT,
    detail     TEXT
);

CREATE INDEX idx_auth_events_at    ON auth_events (at DESC);
CREATE INDEX idx_auth_events_login ON auth_events (login_id, at DESC);
CREATE INDEX idx_auth_events_event ON auth_events (event, at DESC);
