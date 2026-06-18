-- contest-api owns the contest domain. It shares the `contest` database with
-- auth-service but keeps its own Flyway history (flyway_history_contest).
-- The submissions + standings tables arrive with the judging pipeline (later days);
-- when they do, submissions.user_id references users WITHOUT a DB-level FK
-- (users belongs to auth-service — clean service boundary).

CREATE TABLE contests (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title     TEXT        NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    state     TEXT        NOT NULL DEFAULT 'draft'   -- draft | published | running | finished
);

CREATE TABLE problems (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contest_id        BIGINT NOT NULL REFERENCES contests(id),
    label             TEXT   NOT NULL,               -- 'A'..'F'
    title             TEXT   NOT NULL,
    statement_md      TEXT   NOT NULL,
    time_limit_ms     INT    NOT NULL DEFAULT 1000,
    memory_limit_mb   INT    NOT NULL DEFAULT 256,
    test_data_version INT    NOT NULL DEFAULT 1,
    UNIQUE (contest_id, label)
);

CREATE TABLE test_cases (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_id      BIGINT  NOT NULL REFERENCES problems(id),
    ordinal         INT     NOT NULL,
    input           TEXT    NOT NULL,
    expected_output TEXT    NOT NULL,
    is_sample       BOOLEAN NOT NULL DEFAULT FALSE,   -- only samples are ever sent to clients
    UNIQUE (problem_id, ordinal)
);
