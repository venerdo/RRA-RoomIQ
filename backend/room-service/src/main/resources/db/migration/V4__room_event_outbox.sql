CREATE TABLE room_event_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    CONSTRAINT uq_room_event_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_room_event_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_room_event_outbox_pending
    ON room_event_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX ix_room_event_outbox_aggregate_version
    ON room_event_outbox (aggregate_type, aggregate_id, aggregate_version);
