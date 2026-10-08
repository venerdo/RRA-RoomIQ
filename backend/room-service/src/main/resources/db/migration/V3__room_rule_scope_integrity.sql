ALTER TABLE room_rule
    ADD CONSTRAINT ck_room_rule_exactly_one_scope
    CHECK ((room_id IS NOT NULL AND office_building_id IS NULL)
        OR (room_id IS NULL AND office_building_id IS NOT NULL));