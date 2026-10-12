-- The league's card catalogue: each distinct card someone has entered. Search only shows verified cards
-- (approved at least once), so typos don't spread.
CREATE TABLE card (
    id          UUID PRIMARY KEY,
    league_id   UUID        NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    player_name VARCHAR(60) NOT NULL,
    card_type   VARCHAR(20) NOT NULL,
    rating      SMALLINT    NOT NULL CHECK (rating BETWEEN 40 AND 110),
    position    VARCHAR(4)  NOT NULL,
    verified    BOOLEAN     NOT NULL DEFAULT false,
    created_by  UUID        NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_card_identity ON card (league_id, lower(player_name), card_type, rating, position);

-- A card in a manager's squad. REMOVED rows stay so goals credited to them keep their link.
CREATE TABLE squad_card (
    id            UUID PRIMARY KEY,
    league_id     UUID         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES app_user (id),
    card_id       UUID         NOT NULL REFERENCES card (id),
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REMOVED')),
    reject_reason VARCHAR(200),
    decided_by    UUID REFERENCES app_user (id),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_squad_card_owner ON squad_card (league_id, user_id);

-- A manager holds a card at most once (rejected and removed entries don't count).
CREATE UNIQUE INDEX uq_squad_card_live ON squad_card (league_id, user_id, card_id) WHERE status IN ('PENDING', 'APPROVED');

-- Squad rules and the transfer window, one row per league. Null limits are off.
CREATE TABLE squad_rules (
    league_id         UUID        PRIMARY KEY REFERENCES league (id) ON DELETE CASCADE,
    max_squad_size    SMALLINT CHECK (max_squad_size BETWEEN 1 AND 100),
    cap_type          VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (cap_type IN ('NONE', 'PER_CARD', 'SQUAD_TOTAL', 'TOP_11_TOTAL')),
    cap_value         INT CHECK (cap_value BETWEEN 1 AND 100000),
    banned_card_types VARCHAR(20)[] NOT NULL DEFAULT '{}',
    window_open       BOOLEAN     NOT NULL DEFAULT true,
    CONSTRAINT ck_squad_rules_cap CHECK ((cap_type = 'NONE') = (cap_value IS NULL))
);

-- Existing leagues: the window is closed while a season is being played.
INSERT INTO squad_rules (league_id, window_open)
SELECT l.id, NOT EXISTS (SELECT 1 FROM season s WHERE s.league_id = l.id AND s.status = 'ACTIVE')
FROM league l;

-- Scorers and assists picked from squads. The name columns keep a snapshot of the player's name.
ALTER TABLE report_goal
    ADD COLUMN scorer_card_id UUID REFERENCES squad_card (id),
    ADD COLUMN assist_card_id UUID REFERENCES squad_card (id);
