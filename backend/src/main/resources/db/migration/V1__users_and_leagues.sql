-- Accounts. Sign-in providers (Google, Discord) are linked in a later migration.
CREATE TABLE app_user (
    id           UUID PRIMARY KEY,
    display_name VARCHAR(50)  NOT NULL,
    email        VARCHAR(255) UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE league (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    slug       VARCHAR(80) NOT NULL UNIQUE,
    owner_id   UUID        NOT NULL REFERENCES app_user (id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
               CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_league_owner ON league (owner_id);
