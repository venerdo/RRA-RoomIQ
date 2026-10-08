CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    national_id VARCHAR(64) UNIQUE,
    phone VARCHAR(32) UNIQUE,
    password_hash VARCHAR(255),
    full_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    department_id UUID,
    office_building_id UUID,
    status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_app_user_version CHECK (version >= 0)
);

CREATE TABLE role (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    is_system BOOLEAN NOT NULL
);

CREATE TABLE permission (
    id UUID PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    domain VARCHAR(80)
);

CREATE TABLE role_permission (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id),
    CONSTRAINT uq_role_permission_pair UNIQUE (role_id, permission_id)
);

CREATE TABLE user_role (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    scope_office_building_id UUID,
    granted_by_user_id UUID,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_user_role_granted_by FOREIGN KEY (granted_by_user_id) REFERENCES app_user (id)
);

CREATE TABLE user_privilege (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    privilege_code VARCHAR(128) NOT NULL,
    granted_by_user_id UUID,
    valid_from TIMESTAMP WITH TIME ZONE,
    valid_to TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL,
    CONSTRAINT fk_user_privilege_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_privilege_granted_by FOREIGN KEY (granted_by_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_privilege_validity CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);

CREATE TABLE user_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_session_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_app_user_status ON app_user (status);
CREATE INDEX idx_app_user_department ON app_user (department_id);
CREATE INDEX idx_app_user_office_building ON app_user (office_building_id);
CREATE INDEX idx_user_role_user ON user_role (user_id);
CREATE INDEX idx_user_role_scope ON user_role (scope_office_building_id);
CREATE INDEX idx_user_privilege_user_active ON user_privilege (user_id, is_active);
CREATE INDEX idx_user_session_user ON user_session (user_id);
CREATE INDEX idx_user_session_active ON user_session (user_id, revoked_at, expires_at);
