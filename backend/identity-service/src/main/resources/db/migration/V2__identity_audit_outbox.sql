CREATE TABLE identity_audit_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(96) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_identity_audit_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX idx_identity_audit_outbox_pending
    ON identity_audit_outbox (published_at, next_attempt_at, created_at);