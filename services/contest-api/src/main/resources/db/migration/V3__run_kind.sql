-- Adds "Run" (practice against sample tests only) alongside real Submit attempts.
-- kind discriminates the two; passed_tests/total_tests back the "X of Y passed" display.
-- submission_test_results carries a per-test breakdown, but ONLY for kind='run' rows —
-- since a run job only ever judges sample tests (enforced in the orchestrator), this
-- table structurally can never contain hidden-test results.

ALTER TABLE submissions
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'submit'
        CHECK (kind IN ('submit', 'run')),
    ADD COLUMN passed_tests INT,
    ADD COLUMN total_tests INT;

CREATE INDEX idx_sub_kind ON submissions (user_id, kind, created_at DESC);

CREATE TABLE submission_test_results (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id),
    ordinal       INT    NOT NULL,   -- real test_cases.ordinal; only ever a sample ordinal
    verdict       TEXT   NOT NULL,
    exec_time_ms  INT,
    memory_kb     INT,
    UNIQUE (submission_id, ordinal)
);
