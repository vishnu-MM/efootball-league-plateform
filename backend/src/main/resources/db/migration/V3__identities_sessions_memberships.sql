-- Sign-in identities: one user can link several providers (google, discord, dev).
CREATE TABLE user_identity (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, subject)
);

CREATE INDEX idx_user_identity_user ON user_identity (user_id);

-- League membership and per-league role.
CREATE TABLE league_membership (
    league_id UUID        NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (league_id, user_id)
);

CREATE INDEX idx_league_membership_user ON league_membership (user_id);

-- Spring Session JDBC (copied from spring-session-jdbc 4.1.1 schema-postgresql.sql).
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INT      NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
