ALTER TABLE meeting_share_link
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD CONSTRAINT ck_meeting_share_link_idempotency_key
        CHECK (idempotency_key IS NULL OR idempotency_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    ADD CONSTRAINT uq_meeting_share_link_creator_idempotency
        UNIQUE (created_by_user_id, idempotency_key);
