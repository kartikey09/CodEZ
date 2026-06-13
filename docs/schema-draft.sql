-- ============================================================================
-- SCHEMA DRAFT (PostgreSQL 16) — reference only.
-- This is NOT yet a migration. On Day 4 it becomes Flyway V1__init.sql.
-- Auth is admin-issued credentials (no email / no OAuth).
-- Redis holds the live leaderboard; everything here is the durable source of truth.
-- ============================================================================

CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id             TEXT        NOT NULL UNIQUE,          -- admin-issued username / roll no
    password_hash        TEXT        NOT NULL,                 -- bcrypt
    display_name         TEXT        NOT NULL,
    role                 TEXT        NOT NULL DEFAULT 'student', -- student | admin
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN     NOT NULL DEFAULT TRUE,     -- forced change on first login
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contests (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title     TEXT        NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    state     TEXT        NOT NULL DEFAULT 'draft'  -- draft | published | running | finished
);

CREATE TABLE problems (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contest_id        BIGINT NOT NULL REFERENCES contests(id),
    label             TEXT   NOT NULL,              -- 'A'..'F'
    title             TEXT   NOT NULL,
    statement_md      TEXT   NOT NULL,
    time_limit_ms     INT    NOT NULL DEFAULT 1000, -- base; per-language multipliers applied in orchestrator
    memory_limit_mb   INT    NOT NULL DEFAULT 256,
    test_data_version INT    NOT NULL DEFAULT 1,    -- bump on test edits -> cache bust + rejudge marker
    UNIQUE (contest_id, label)
);

CREATE TABLE test_cases (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_id      BIGINT  NOT NULL REFERENCES problems(id),
    ordinal         INT     NOT NULL,
    input           TEXT    NOT NULL,
    expected_output TEXT    NOT NULL,
    is_sample       BOOLEAN NOT NULL DEFAULT FALSE,  -- samples shown; hidden ones NEVER serialized to clients
    UNIQUE (problem_id, ordinal)
);

CREATE TABLE submissions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    problem_id   BIGINT NOT NULL REFERENCES problems(id),
    contest_id   BIGINT NOT NULL REFERENCES contests(id),
    language     TEXT   NOT NULL,                  -- c | cpp | java | python (allowlist in API)
    source_code  TEXT   NOT NULL,                  -- <= 64 KB enforced at API
    status       TEXT   NOT NULL DEFAULT 'queued', -- queued | running | done
    verdict      TEXT,                             -- AC|WA|TLE|MLE|RE|CE|IE
    failed_test  INT,                              -- ordinal of first failing test (never leak its data)
    exec_time_ms INT,
    memory_kb    INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    judged_at    TIMESTAMPTZ
);
CREATE INDEX idx_sub_user    ON submissions (user_id, created_at DESC);
CREATE INDEX idx_sub_problem ON submissions (problem_id, created_at);
CREATE INDEX idx_sub_pending ON submissions (status) WHERE status IN ('queued','running');

-- Optional durable snapshot of standings (recovery is also possible by replaying submissions).
CREATE TABLE standings_snapshots (
    contest_id BIGINT      NOT NULL,
    taken_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload    JSONB       NOT NULL
);
