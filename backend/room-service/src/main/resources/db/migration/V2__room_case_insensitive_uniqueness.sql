CREATE UNIQUE INDEX uq_room_building_name_ci ON room (office_building_id, LOWER(name));
CREATE UNIQUE INDEX uq_room_building_code_ci ON room (office_building_id, LOWER(code));