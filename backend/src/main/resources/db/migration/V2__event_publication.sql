-- Spring Modulith event publication registry (transactional outbox).
-- Events published between modules are stored here so they survive restarts
-- and can be retried if a listener fails. Columns mirror Modulith's JPA mapping.
CREATE TABLE event_publication (
    id                     UUID PRIMARY KEY,
    listener_id            VARCHAR(512) NOT NULL,
    event_type             VARCHAR(512) NOT NULL,
    serialized_event       TEXT         NOT NULL,
    publication_date       TIMESTAMPTZ  NOT NULL,
    completion_date        TIMESTAMPTZ,
    status                 VARCHAR(20),
    completion_attempts    INT          NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ
);

CREATE INDEX idx_event_publication_incomplete
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;
