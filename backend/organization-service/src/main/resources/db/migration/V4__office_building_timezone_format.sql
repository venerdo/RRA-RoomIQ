ALTER TABLE office_building
    ADD CONSTRAINT ck_office_building_timezone_format
    CHECK (
        timezone = TRIM(timezone)
        AND POSITION(' ' IN timezone) = 0
        AND SUBSTRING(timezone FROM 1 FOR 1) <> '/'
        AND SUBSTRING(timezone FROM CHAR_LENGTH(timezone) FOR 1) <> '/'
        AND POSITION('//' IN timezone) = 0
    );