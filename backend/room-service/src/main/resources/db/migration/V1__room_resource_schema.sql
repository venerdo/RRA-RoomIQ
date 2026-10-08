CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE room_type (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_room_type_code UNIQUE (code),
    CONSTRAINT ck_room_type_code_not_blank CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_room_type_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);

CREATE TABLE facility_type (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_facility_type_code UNIQUE (code),
    CONSTRAINT ck_facility_type_code_not_blank CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_facility_type_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);

CREATE TABLE room (
    id UUID PRIMARY KEY,
    floor_id UUID NOT NULL,
    office_building_id UUID NOT NULL,
    room_type_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description TEXT,
    capacity INTEGER NOT NULL,
    class VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_room_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id),
    CONSTRAINT uq_room_building_name UNIQUE (office_building_id, name),
    CONSTRAINT uq_room_building_code UNIQUE (office_building_id, code),
    CONSTRAINT ck_room_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_room_code_not_blank CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_room_capacity_positive CHECK (capacity > 0),
    CONSTRAINT ck_room_class CHECK (class IN ('NORMAL', 'VIP')),
    CONSTRAINT ck_room_status CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'TEMP_UNAVAILABLE', 'DECOMMISSIONED')),
    CONSTRAINT ck_room_version_nonnegative CHECK (version >= 0)
);

CREATE TABLE room_facility (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    facility_type_id UUID NOT NULL,
    quantity SMALLINT NOT NULL DEFAULT 1,
    state VARCHAR(16) NOT NULL,
    last_serviced_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_room_facility_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_room_facility_type FOREIGN KEY (facility_type_id) REFERENCES facility_type (id),
    CONSTRAINT uq_room_facility_type UNIQUE (room_id, facility_type_id),
    CONSTRAINT ck_room_facility_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_room_facility_state CHECK (state IN ('WORKING', 'FAULTY', 'REMOVED'))
);

CREATE TABLE room_photo (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    secure_url TEXT NOT NULL,
    sort_order SMALLINT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    approved_for_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_photo_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT uq_room_photo_cloudinary_public_id UNIQUE (cloudinary_public_id),
    CONSTRAINT ck_room_photo_public_id_not_blank CHECK (CHAR_LENGTH(TRIM(cloudinary_public_id)) > 0),
    CONSTRAINT ck_room_photo_url_not_blank CHECK (CHAR_LENGTH(TRIM(secure_url)) > 0),
    CONSTRAINT ck_room_photo_sort_order_nonnegative CHECK (sort_order IS NULL OR sort_order >= 0)
);

CREATE TABLE room_rule (
    id UUID PRIMARY KEY,
    room_id UUID,
    office_building_id UUID,
    min_duration_minutes INTEGER,
    max_duration_minutes INTEGER,
    min_advance_minutes INTEGER,
    max_advance_days INTEGER,
    cancellation_deadline_minutes INTEGER,
    recurring_allowed BOOLEAN NOT NULL,
    external_guests_allowed BOOLEAN NOT NULL,
    approval_required BOOLEAN NOT NULL,
    outside_hours_allowed BOOLEAN NOT NULL,
    release_buffer_minutes INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_room_rule_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT ck_room_rule_min_duration_positive CHECK (min_duration_minutes IS NULL OR min_duration_minutes > 0),
    CONSTRAINT ck_room_rule_max_duration_positive CHECK (max_duration_minutes IS NULL OR max_duration_minutes > 0),
    CONSTRAINT ck_room_rule_duration_bounds CHECK (
        min_duration_minutes IS NULL OR max_duration_minutes IS NULL OR min_duration_minutes <= max_duration_minutes
    ),
    CONSTRAINT ck_room_rule_min_advance_nonnegative CHECK (min_advance_minutes IS NULL OR min_advance_minutes >= 0),
    CONSTRAINT ck_room_rule_max_advance_nonnegative CHECK (max_advance_days IS NULL OR max_advance_days >= 0),
    CONSTRAINT ck_room_rule_cancellation_nonnegative CHECK (
        cancellation_deadline_minutes IS NULL OR cancellation_deadline_minutes >= 0
    ),
    CONSTRAINT ck_room_rule_release_buffer_nonnegative CHECK (release_buffer_minutes >= 0)
);

CREATE TABLE room_rule_allowed_department (
    id UUID PRIMARY KEY,
    room_rule_id UUID NOT NULL,
    department_id UUID NOT NULL,
    CONSTRAINT fk_room_rule_allowed_department_rule FOREIGN KEY (room_rule_id) REFERENCES room_rule (id),
    CONSTRAINT uq_room_rule_allowed_department UNIQUE (room_rule_id, department_id)
);

CREATE TABLE room_status_history (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    old_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_by_user_id UUID NOT NULL,
    reason TEXT,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_status_history_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT ck_room_status_history_old_status CHECK (
        old_status IS NULL OR old_status IN ('AVAILABLE', 'MAINTENANCE', 'TEMP_UNAVAILABLE', 'DECOMMISSIONED')
    ),
    CONSTRAINT ck_room_status_history_new_status CHECK (
        new_status IN ('AVAILABLE', 'MAINTENANCE', 'TEMP_UNAVAILABLE', 'DECOMMISSIONED')
    )
);

CREATE TABLE maintenance_period (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    period TSTZRANGE NOT NULL,
    reason TEXT,
    created_by_user_id UUID NOT NULL,
    CONSTRAINT fk_maintenance_period_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT ck_maintenance_period_not_empty CHECK (NOT isempty(period)),
    CONSTRAINT ex_maintenance_period_room_overlap EXCLUDE USING GIST (room_id WITH =, period WITH &&)
);

CREATE INDEX idx_room_office_status ON room (office_building_id, status);
CREATE INDEX idx_room_floor ON room (floor_id);
CREATE INDEX idx_room_room_type ON room (room_type_id);
CREATE INDEX idx_room_facility_type ON room_facility (facility_type_id);
CREATE INDEX idx_room_photo_room_order ON room_photo (room_id, sort_order);
CREATE INDEX idx_room_rule_building_active_effective ON room_rule (office_building_id, is_active, effective_from);
CREATE INDEX idx_room_rule_allowed_department_department ON room_rule_allowed_department (department_id);
CREATE INDEX idx_room_status_history_room_changed ON room_status_history (room_id, changed_at DESC);