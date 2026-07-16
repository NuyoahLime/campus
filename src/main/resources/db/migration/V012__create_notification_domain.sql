-- V012: notification domain (notifications)
-- FK count: 1

CREATE TABLE notifications (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    recipient_id    uuid          NOT NULL,
    event_type      varchar(64)   NOT NULL,
    title           varchar(300)  NOT NULL,
    content         text          NULL,
    reference_type  varchar(32)   NULL,
    reference_id    uuid          NULL,
    is_read         boolean       NOT NULL DEFAULT false,
    read_at         timestamptz   NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_notification_recipient_unread
    ON notifications(recipient_id, is_read, created_at);
