ALTER TABLE department
    ADD CONSTRAINT ck_department_not_own_parent
    CHECK (parent_department_id IS NULL OR parent_department_id <> id);