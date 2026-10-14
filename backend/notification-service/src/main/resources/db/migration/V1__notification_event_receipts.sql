CREATE TABLE notification_event_receipt (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    source_service VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(128),
    causation_id VARCHAR(128),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_event_receipt_event_id UNIQUE (event_id),
    CONSTRAINT ck_notification_event_receipt_event_version CHECK (event_version > 0),
    CONSTRAINT ck_notification_event_receipt_aggregate_version CHECK (aggregate_version >= 0)
);

CREATE TABLE notification (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    notification_template_id UUID,
    event_type VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_id UUID,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    payload JSONB,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX ix_notification_recipient_created
    ON notification (recipient_user_id, created_at DESC);
