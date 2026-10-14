ALTER TABLE notification
    ADD COLUMN scheduled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE notification
    DROP CONSTRAINT ck_notification_status,
    ADD CONSTRAINT ck_notification_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD_LETTER', 'SCHEDULED', 'CANCELLED')),
    ADD CONSTRAINT ck_notification_scheduled_at
        CHECK ((status = 'SCHEDULED') = (scheduled_at IS NOT NULL)),
    ADD CONSTRAINT ck_notification_cancelled_at
        CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL));

CREATE INDEX ix_notification_scheduled_due
    ON notification (scheduled_at, id)
    WHERE status = 'SCHEDULED';

CREATE UNIQUE INDEX ux_notification_reminder_schedule
    ON notification (recipient_user_id, channel, resource_type, resource_id, scheduled_at)
    WHERE event_type = 'Reminder';

ALTER TABLE notification_event_receipt
    ADD COLUMN resource_type VARCHAR(64),
    ADD COLUMN resource_id UUID;

CREATE INDEX ix_notification_event_receipt_resource
    ON notification_event_receipt (resource_type, resource_id, event_type);
