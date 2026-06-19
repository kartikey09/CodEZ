-- Submissions land here (the submit endpoint + pipeline arrive on Day 5).
-- user_id references users, which belongs to auth-service, so there is NO
-- DB-level foreign key to users — the service boundary stays clean.
-- problem_id and contest_id are intra-service, so those FKs stay.

CREATE TABLE submissions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL,                  -- -> users (auth-service); no FK on purpose
    problem_id   BIGINT      NOT NULL REFERENCES problems(id),
    contest_id   BIGINT      NOT NULL REFERENCES contests(id),
    language     TEXT        NOT NULL,                  -- c | cpp | java | python (allowlist at API)
    source_code  TEXT        NOT NULL,                  -- <= 64 KB enforced at the API
    status       TEXT        NOT NULL DEFAULT 'queued', -- queued | running | done
    verdict      TEXT,                                  -- AC|WA|TLE|MLE|RE|CE|IE
    failed_test  INT,                                   -- ordinal of first failing test (never its data)
    exec_time_ms INT,
    memory_kb    INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    judged_at    TIMESTAMPTZ
);

CREATE INDEX idx_sub_user    ON submissions (user_id, created_at DESC);
CREATE INDEX idx_sub_problem ON submissions (problem_id, created_at);
CREATE INDEX idx_sub_pending ON submissions (status) WHERE status IN ('queued', 'running');
