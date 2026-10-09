CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE booking_request (
    id UUID PRIMARY KEY,
    request_reference VARCHAR NOT NULL,
    request_type VARCHAR NOT NULL,
    requested_by_user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    room_id UUID NOT NULL,
    office_building_id UUID NOT NULL,
    recurrence_rule_id UUID,
    title VARCHAR NOT NULL,
    purpose TEXT,
    requested_start TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_end TIMESTAMP WITH TIME ZONE NOT NULL,
    attendee_count INTEGER NOT NULL,
    external_guests BOOLEAN,
    status VARCHAR NOT NULL,
    idempotency_key VARCHAR,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_booking_request_reference UNIQUE (request_reference),
    CONSTRAINT uq_booking_request_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_booking_request_type CHECK (request_type IN ('SECRETARY_REQUEST', 'ADMIN_DIRECT_BOOKING')),
    CONSTRAINT ck_booking_request_status CHECK (
        status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_booking_request_interval CHECK (requested_end > requested_start),
    CONSTRAINT ck_booking_request_attendee_count CHECK (attendee_count > 0)
);

CREATE TABLE approval_decision (
    id UUID PRIMARY KEY,
    booking_request_id UUID NOT NULL,
    decided_by_user_id UUID NOT NULL,
    decision VARCHAR NOT NULL,
    comment TEXT,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_approval_decision_booking_request FOREIGN KEY (booking_request_id)
        REFERENCES booking_request (id),
    CONSTRAINT uq_approval_decision_booking_request UNIQUE (booking_request_id),
    CONSTRAINT ck_approval_decision_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE TABLE reservation (
    id UUID PRIMARY KEY,
    booking_request_id UUID NOT NULL,
    room_id UUID NOT NULL,
    organizer_user_id UUID NOT NULL,
    recurrence_rule_id UUID,
    occupied_period TSTZRANGE NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    release_buffer_minutes INTEGER NOT NULL DEFAULT 5,
    status VARCHAR NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_booking_request FOREIGN KEY (booking_request_id)
        REFERENCES booking_request (id),
    CONSTRAINT uq_reservation_booking_request UNIQUE (booking_request_id),
    CONSTRAINT ck_reservation_status CHECK (
        status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'RELEASED')
    ),
    CONSTRAINT ex_reservation_room_occupied_period EXCLUDE USING GIST (
        room_id WITH =,
        occupied_period WITH &&
    )
);

CREATE TABLE meeting (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    title VARCHAR NOT NULL,
    agenda TEXT,
    organizer_display_name VARCHAR NOT NULL,
    instructions TEXT,
    contact_info VARCHAR,
    visibility VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_meeting_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservation (id),
    CONSTRAINT uq_meeting_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_meeting_visibility CHECK (visibility IN ('PRIVATE', 'INTERNAL', 'PUBLIC'))
);

CREATE TABLE meeting_participant (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL,
    user_id UUID,
    external_email VARCHAR,
    display_name VARCHAR,
    role VARCHAR NOT NULL,
    invite_status VARCHAR NOT NULL,
    CONSTRAINT fk_meeting_participant_meeting FOREIGN KEY (meeting_id)
        REFERENCES meeting (id),
    CONSTRAINT ck_meeting_participant_identity CHECK (user_id IS NOT NULL OR external_email IS NOT NULL),
    CONSTRAINT ck_meeting_participant_role CHECK (role IN ('ORGANIZER', 'REQUIRED', 'OPTIONAL')),
    CONSTRAINT ck_meeting_participant_invite_status CHECK (invite_status IN ('INVITED', 'ACCEPTED', 'DECLINED'))
);

CREATE TABLE meeting_share_link (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL,
    token_hash VARCHAR NOT NULL,
    created_by_user_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    view_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_meeting_share_link_meeting FOREIGN KEY (meeting_id)
        REFERENCES meeting (id),
    CONSTRAINT uq_meeting_share_link_token_hash UNIQUE (token_hash)
);

CREATE TABLE booking_extension (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    previous_end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_end_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR NOT NULL,
    decided_by_user_id UUID,
    decision_comment TEXT,
    idempotency_key VARCHAR,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_booking_extension_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservation (id),
    CONSTRAINT uq_booking_extension_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_booking_extension_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_booking_extension_requested_end CHECK (requested_end_at > previous_end_at)
);

CREATE TABLE cancellation (
    id UUID PRIMARY KEY,
    reservation_id UUID,
    booking_request_id UUID,
    cancelled_by_user_id UUID NOT NULL,
    reason_code VARCHAR NOT NULL,
    reason TEXT NOT NULL,
    cancellation_deadline_at TIMESTAMP WITH TIME ZONE,
    override_applied BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason TEXT,
    cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cancellation_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservation (id),
    CONSTRAINT fk_cancellation_booking_request FOREIGN KEY (booking_request_id)
        REFERENCES booking_request (id),
    CONSTRAINT ck_cancellation_reason_code CHECK (
        reason_code IN ('USER', 'ADMIN', 'MAINTENANCE', 'NO_SHOW', 'POLICY')
    ),
    CONSTRAINT ck_cancellation_override_reason CHECK (NOT override_applied OR override_reason IS NOT NULL)
);
