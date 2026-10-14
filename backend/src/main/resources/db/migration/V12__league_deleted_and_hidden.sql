-- Soft delete (the owner can restore for 7 days, then the league is purged) and moderator hiding.
ALTER TABLE league
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN hidden_at  TIMESTAMPTZ;

CREATE INDEX idx_league_deleted_at ON league (deleted_at) WHERE deleted_at IS NOT NULL;
