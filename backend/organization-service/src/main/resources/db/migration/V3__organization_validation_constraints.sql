ALTER TABLE country
    ADD CONSTRAINT ck_country_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0);

ALTER TABLE province
    ADD CONSTRAINT ck_province_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0);

ALTER TABLE district
    ADD CONSTRAINT ck_district_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0);

ALTER TABLE office_building
    ADD CONSTRAINT ck_office_building_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0);

ALTER TABLE office_building
    ADD CONSTRAINT ck_office_building_code_not_blank CHECK (CHAR_LENGTH(TRIM(code)) > 0);

ALTER TABLE office_building
    ADD CONSTRAINT ck_office_building_timezone_not_blank CHECK (CHAR_LENGTH(TRIM(timezone)) > 0);

ALTER TABLE floor
    ADD CONSTRAINT ck_floor_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0);

ALTER TABLE department
    ADD CONSTRAINT ck_department_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0);

ALTER TABLE department
    ADD CONSTRAINT ck_department_code_not_blank CHECK (CHAR_LENGTH(TRIM(code)) > 0);