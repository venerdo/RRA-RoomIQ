CREATE UNIQUE INDEX uq_working_calendar_office_building
    ON working_calendar (office_building_id)
    WHERE office_building_id IS NOT NULL;