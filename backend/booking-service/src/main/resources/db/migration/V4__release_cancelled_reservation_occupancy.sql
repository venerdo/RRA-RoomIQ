ALTER TABLE reservation
    DROP CONSTRAINT ex_reservation_room_occupied_period;

ALTER TABLE reservation
    ADD CONSTRAINT ex_reservation_room_occupied_period
        EXCLUDE USING GIST (
            room_id WITH =,
            occupied_period WITH &&
        )
        WHERE (status NOT IN ('CANCELLED', 'RELEASED'));

ALTER TABLE reservation_occurrence
    ADD COLUMN occupancy_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE reservation_occurrence
    DROP CONSTRAINT ex_reservation_occurrence_room_occupied_period;

ALTER TABLE reservation_occurrence
    ADD CONSTRAINT ex_reservation_occurrence_room_occupied_period
        EXCLUDE USING GIST (
            room_id WITH =,
            occupied_period WITH &&
        )
        WHERE (occupancy_active = TRUE);
