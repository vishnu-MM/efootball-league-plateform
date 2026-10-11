-- A member's club name in one league. Names are unique within a league, ignoring case.
CREATE TABLE club (
    league_id  UUID        NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name       VARCHAR(40) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (league_id, user_id)
);

CREATE UNIQUE INDEX uq_club_league_name ON club (league_id, lower(name));

-- Seasons. A league has at most one ACTIVE season. Play-by dates need both start_date and days_per_round.
CREATE TABLE season (
    id             UUID PRIMARY KEY,
    league_id      UUID        NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    number         INT         NOT NULL,
    name           VARCHAR(60) NOT NULL,
    format         VARCHAR(20) NOT NULL CHECK (format IN ('SINGLE', 'HOME_AND_AWAY')),
    points_win     SMALLINT    NOT NULL CHECK (points_win BETWEEN 1 AND 10),
    points_draw    SMALLINT    NOT NULL CHECK (points_draw >= 0 AND points_draw <= points_win),
    start_date     DATE,
    days_per_round SMALLINT CHECK (days_per_round BETWEEN 1 AND 60),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED')),
    created_by     UUID        NOT NULL REFERENCES app_user (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    CONSTRAINT uq_season_number UNIQUE (league_id, number),
    CONSTRAINT ck_season_timing CHECK ((start_date IS NULL) = (days_per_round IS NULL))
);

CREATE UNIQUE INDEX uq_season_active ON season (league_id) WHERE status = 'ACTIVE';

CREATE TABLE season_participant (
    season_id UUID NOT NULL REFERENCES season (id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (season_id, user_id)
);

CREATE INDEX idx_season_participant_user ON season_participant (user_id);

CREATE TABLE fixture (
    id            UUID PRIMARY KEY,
    season_id     UUID        NOT NULL REFERENCES season (id) ON DELETE CASCADE,
    round         SMALLINT    NOT NULL CHECK (round >= 1),
    home_user_id  UUID        NOT NULL REFERENCES app_user (id),
    away_user_id  UUID        NOT NULL REFERENCES app_user (id),
    play_by       DATE,
    status        VARCHAR(30) NOT NULL
        CHECK (status IN ('SCHEDULED', 'AWAITING_CONFIRMATION', 'DISPUTED', 'CONFIRMED')),
    home_goals    SMALLINT,
    away_goals    SMALLINT,
    final_report_id UUID,
    resolved_by   UUID REFERENCES app_user (id),
    finalized_at  TIMESTAMPTZ,
    CONSTRAINT ck_fixture_players CHECK (home_user_id <> away_user_id),
    CONSTRAINT ck_fixture_final_score CHECK ((status = 'CONFIRMED') = (home_goals IS NOT NULL AND away_goals IS NOT NULL))
);

CREATE INDEX idx_fixture_season_round ON fixture (season_id, round);

-- Every score entered for a fixture: the first report, a disputing counter-report, an admin resolution.
CREATE TABLE score_report (
    id          UUID PRIMARY KEY,
    fixture_id  UUID        NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL CHECK (kind IN ('REPORT', 'DISPUTE', 'RESOLUTION')),
    reported_by UUID        NOT NULL REFERENCES app_user (id),
    home_goals  SMALLINT    NOT NULL CHECK (home_goals BETWEEN 0 AND 99),
    away_goals  SMALLINT    NOT NULL CHECK (away_goals BETWEEN 0 AND 99),
    note        VARCHAR(280),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_score_report_fixture ON score_report (fixture_id);

-- The report whose score (and scorers) became the official result.
ALTER TABLE fixture ADD CONSTRAINT fk_fixture_final_report FOREIGN KEY (final_report_id) REFERENCES score_report (id);

-- Optional scorers (and assists) attached to a report, in the order they were entered.
CREATE TABLE report_goal (
    report_id UUID        NOT NULL REFERENCES score_report (id) ON DELETE CASCADE,
    position  INT         NOT NULL,
    side      VARCHAR(4)  NOT NULL CHECK (side IN ('HOME', 'AWAY')),
    scorer    VARCHAR(60) NOT NULL,
    assist    VARCHAR(60),
    PRIMARY KEY (report_id, position)
);
