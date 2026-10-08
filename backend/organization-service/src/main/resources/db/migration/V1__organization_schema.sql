CREATE TABLE country (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    iso_code CHAR(2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_country_name UNIQUE (name),
    CONSTRAINT uq_country_iso_code UNIQUE (iso_code)
);

CREATE TABLE province (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_province_country FOREIGN KEY (country_id) REFERENCES country (id),
    CONSTRAINT uq_province_country_name UNIQUE (country_id, name)
);

CREATE TABLE district (
    id UUID PRIMARY KEY,
    province_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_district_province FOREIGN KEY (province_id) REFERENCES province (id),
    CONSTRAINT uq_district_province_name UNIQUE (province_id, name)
);

CREATE TABLE office_building (
    id UUID PRIMARY KEY,
    district_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(64) NOT NULL,
    address TEXT,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Africa/Kigali',
    working_calendar_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_office_building_district FOREIGN KEY (district_id) REFERENCES district (id),
    CONSTRAINT uq_office_building_code UNIQUE (code)
);

CREATE TABLE floor (
    id UUID PRIMARY KEY,
    office_building_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    level SMALLINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_floor_office_building FOREIGN KEY (office_building_id) REFERENCES office_building (id),
    CONSTRAINT uq_floor_building_name UNIQUE (office_building_id, name)
);

CREATE TABLE department (
    id UUID PRIMARY KEY,
    office_building_id UUID,
    parent_department_id UUID,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_department_office_building FOREIGN KEY (office_building_id) REFERENCES office_building (id),
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_department_id) REFERENCES department (id),
    CONSTRAINT uq_department_code UNIQUE (code),
    CONSTRAINT ck_department_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_province_country_id ON province (country_id);
CREATE INDEX idx_district_province_id ON district (province_id);
CREATE INDEX idx_office_building_district_id ON office_building (district_id);
CREATE INDEX idx_floor_office_building_id ON floor (office_building_id);
CREATE INDEX idx_department_office_building_id ON department (office_building_id);
CREATE INDEX idx_department_parent_id ON department (parent_department_id);