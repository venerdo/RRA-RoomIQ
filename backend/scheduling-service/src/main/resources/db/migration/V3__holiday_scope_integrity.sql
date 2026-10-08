CREATE UNIQUE INDEX uq_holiday_scope_date
    ON holiday (working_calendar_id, office_building_id, holiday_date) NULLS NOT DISTINCT;