-- A league's Discord channel webhook and which kinds of events it posts. The URL is a secret: never sent back in full.
CREATE TABLE league_webhook (
    league_id     UUID          PRIMARY KEY REFERENCES league (id) ON DELETE CASCADE,
    url           VARCHAR(300)  NOT NULL,
    event_types   VARCHAR(30)[] NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT true,
    last_error    VARCHAR(300),
    last_error_at TIMESTAMPTZ,
    updated_by    UUID          NOT NULL REFERENCES app_user (id),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
