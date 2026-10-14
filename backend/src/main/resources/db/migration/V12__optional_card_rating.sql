-- Card rating is optional.
ALTER TABLE card ALTER COLUMN rating DROP NOT NULL;

DROP INDEX IF EXISTS uq_card_identity;
CREATE UNIQUE INDEX uq_card_identity ON card (league_id, lower(player_name), card_type, rating, position) NULLS NOT DISTINCT;
