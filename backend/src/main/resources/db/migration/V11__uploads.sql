-- Screenshots stored in S3-compatible storage. A row is created on upload and marked attached when a card or a
-- score report takes it; unattached uploads are cleaned up after a day.
CREATE TABLE upload (
    id           UUID PRIMARY KEY,
    league_id    UUID         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    uploaded_by  UUID         NOT NULL REFERENCES app_user (id),
    purpose      VARCHAR(20)  NOT NULL CHECK (purpose IN ('CARD_PROOF', 'DISPUTE_EVIDENCE')),
    content_type VARCHAR(20)  NOT NULL,
    size_bytes   INT          NOT NULL CHECK (size_bytes > 0),
    storage_key  VARCHAR(200) NOT NULL,
    attached_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_upload_unattached ON upload (created_at) WHERE attached_at IS NULL;

-- Screenshots attached to a score report or dispute, in the order they were added.
CREATE TABLE report_evidence (
    report_id UUID     NOT NULL REFERENCES score_report (id) ON DELETE CASCADE,
    position  INT      NOT NULL,
    upload_id UUID     NOT NULL REFERENCES upload (id),
    PRIMARY KEY (report_id, position)
);

ALTER TABLE squad_card ADD COLUMN proof_upload_id UUID REFERENCES upload (id);

ALTER TABLE squad_rules ADD COLUMN require_card_proof BOOLEAN NOT NULL DEFAULT false;
