ALTER TABLE reservation
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE reservation
    ADD CONSTRAINT ck_reservation_completed_at_status
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL));
