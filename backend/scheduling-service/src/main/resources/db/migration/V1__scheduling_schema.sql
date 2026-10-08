CREATE TABLE working_calendar (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    office_building_id UUID,
    timezone VARCHAR(64) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_working_calendar_name UNIQUE (name),
    CONSTRAINT ck_working_calendar_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_working_calendar_timezone_not_blank CHECK (CHAR_LENGTH(TRIM(timezone)) > 0)
);

CREATE TABLE working_day_window (
    id UUID PRIMARY KEY,
    working_calendar_id UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    is_working_day BOOLEAN NOT NULL,
    CONSTRAINT fk_working_day_window_calendar FOREIGN KEY (working_calendar_id)
        REFERENCES working_calendar (id),
    CONSTRAINT ck_working_day_window_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_working_day_window_order CHECK (close_time > open_time)
);

CREATE TABLE holiday (
    id UUID PRIMARY KEY,
    working_calendar_id UUID,
    office_building_id UUID,
    holiday_date DATE NOT NULL,
    name VARCHAR(200) NOT NULL,
    blocks_booking BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    CONSTRAINT fk_holiday_calendar FOREIGN KEY (working_calendar_id)
        REFERENCES working_calendar (id),
    CONSTRAINT uq_holiday_calendar_date UNIQUE (working_calendar_id, holiday_date),
    CONSTRAINT ck_holiday_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0)
);

CREATE TABLE closure_period (
    id UUID PRIMARY KEY,
    office_building_id UUID,
    period TSTZRANGE NOT NULL,
    reason TEXT,
    blocks_booking BOOLEAN NOT NULL,
    CONSTRAINT ck_closure_period_not_empty CHECK (NOT isempty(period))
);

CREATE TABLE recurrence_rule (
    id UUID PRIMARY KEY,
    rrule VARCHAR(1000) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE,
    occurrence_count INTEGER,
    timezone VARCHAR(64) NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_recurrence_rule_not_blank CHECK (CHAR_LENGTH(TRIM(rrule)) > 0),
    CONSTRAINT ck_recurrence_rule_timezone_not_blank CHECK (CHAR_LENGTH(TRIM(timezone)) > 0),
    CONSTRAINT ck_recurrence_rule_end_date CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_recurrence_rule_occurrence_count CHECK (occurrence_count IS NULL OR occurrence_count > 0)
);

CREATE INDEX idx_working_day_window_calendar_day
    ON working_day_window (working_calendar_id, day_of_week);
CREATE INDEX idx_holiday_calendar_date
    ON holiday (working_calendar_id, holiday_date);
CREATE INDEX idx_holiday_building_date
    ON holiday (office_building_id, holiday_date);
CREATE INDEX idx_holiday_active_date
    ON holiday (is_active, holiday_date);
CREATE INDEX idx_closure_period_building
    ON closure_period (office_building_id);
CREATE INDEX idx_recurrence_rule_starts_on
    ON recurrence_rule (starts_on);