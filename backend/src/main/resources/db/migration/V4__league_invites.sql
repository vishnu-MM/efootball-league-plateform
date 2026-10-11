-- Invite links. A league has at most one active link; making a new one revokes the old.
CREATE TABLE league_invite (
    code       VARCHAR(16) PRIMARY KEY,
    league_id  UUID        NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    created_by UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_league_invite_active ON league_invite (league_id) WHERE revoked_at IS NULL;
