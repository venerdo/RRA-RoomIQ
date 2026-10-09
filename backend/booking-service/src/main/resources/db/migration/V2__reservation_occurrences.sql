CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservation_occurrence (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    room_id UUID NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    occupied_period TSTZRANGE NOT NULL,
    CONSTRAINT fk_reservation_occurrence_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservation (id),
    CONSTRAINT uq_reservation_occurrence_series_start UNIQUE (reservation_id, start_at),
    CONSTRAINT ck_reservation_occurrence_interval CHECK (end_at > start_at),
    CONSTRAINT ck_reservation_occurrence_period CHECK (
        isempty(occupied_period)
        OR (
            lower(occupied_period) = start_at
            AND lower_inc(occupied_period)
            AND NOT upper_inc(occupied_period)
            AND upper(occupied_period) >= end_at
        )
    ),
    CONSTRAINT ex_reservation_occurrence_room_occupied_period EXCLUDE USING GIST (
        room_id WITH =,
        occupied_period WITH &&
    )
);

CREATE INDEX ix_reservation_occurrence_room_start
    ON reservation_occurrence (room_id, start_at);

INSERT INTO reservation_occurrence (
    id,
    reservation_id,
    room_id,
    start_at,
    end_at,
    occupied_period
)
SELECT gen_random_uuid(),
       reservation.id,
       reservation.room_id,
       reservation.start_at,
       reservation.end_at,
       reservation.occupied_period
FROM reservation;
