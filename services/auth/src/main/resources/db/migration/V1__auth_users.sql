-- auth-service owns the shared users table.
-- contest-api's own Flyway (Day 4) creates the contest/problem/submission tables
-- and references user_id WITHOUT a DB-level FK (cross-service boundary).

CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id             TEXT        NOT NULL UNIQUE,           -- admin-issued username / roll no
    password_hash        TEXT        NOT NULL,                  -- bcrypt
    display_name         TEXT        NOT NULL,
    role                 TEXT        NOT NULL DEFAULT 'student', -- student | admin
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
