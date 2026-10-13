-- The match MVP, picked by whoever entered the score. With squads it is an approved card (the name is a snapshot);
-- without, a typed name.
ALTER TABLE score_report
    ADD COLUMN mvp_side    VARCHAR(4) CHECK (mvp_side IN ('HOME', 'AWAY')),
    ADD COLUMN mvp_name    VARCHAR(60),
    ADD COLUMN mvp_card_id UUID REFERENCES squad_card (id),
    ADD CONSTRAINT ck_score_report_mvp CHECK ((mvp_side IS NULL) = (mvp_name IS NULL));
