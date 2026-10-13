-- In-app notifications: one row per recipient. The text is written when it happens.
CREATE TABLE notification (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    league_id  UUID         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    text       VARCHAR(400) NOT NULL,
    link       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    read_at    TIMESTAMPTZ
);

CREATE INDEX idx_notification_user_time ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE read_at IS NULL;
