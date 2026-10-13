-- Admin corrections: an override replaces a final result; a forfeit settles a fixture without it being played.
ALTER TABLE score_report DROP CONSTRAINT score_report_kind_check;
ALTER TABLE score_report ADD CONSTRAINT score_report_kind_check
    CHECK (kind IN ('REPORT', 'DISPUTE', 'RESOLUTION', 'OVERRIDE', 'FORFEIT'));

-- Who forfeited (null when the match was played) and whether an admin overrode the result.
ALTER TABLE fixture
    ADD COLUMN forfeit    VARCHAR(4) CHECK (forfeit IN ('HOME', 'AWAY', 'BOTH')),
    ADD COLUMN overridden BOOLEAN NOT NULL DEFAULT false;

-- Points taken from a participant in a season. Revoked rows stay so the log and history make sense.
CREATE TABLE point_adjustment (
    id            UUID PRIMARY KEY,
    season_id     UUID         NOT NULL REFERENCES season (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES app_user (id),
    points        SMALLINT     NOT NULL CHECK (points BETWEEN 1 AND 99),
    reason        VARCHAR(280) NOT NULL,
    created_by    UUID         NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_by    UUID REFERENCES app_user (id),
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(280),
    CONSTRAINT ck_point_adjustment_revoked CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

CREATE INDEX idx_point_adjustment_season ON point_adjustment (season_id);

-- The league log: what happened, written when it happened (names as they were then). Append-only.
CREATE TABLE league_activity (
    id              UUID PRIMARY KEY,
    league_id       UUID         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    actor_id        UUID REFERENCES app_user (id),
    type            VARCHAR(40)  NOT NULL,
    admin_action    BOOLEAN      NOT NULL,
    summary         VARCHAR(300) NOT NULL,
    reason          VARCHAR(280),
    fixture_id      UUID,
    subject_user_id UUID REFERENCES app_user (id)
);

CREATE INDEX idx_league_activity_league_time ON league_activity (league_id, created_at DESC);
