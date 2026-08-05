--liquibase formatted sql

--changeset averpo:022-app-user
-- Тизим фойдаланувчилари. Биринчи admin'ни AdminUserInitializer
-- яратади (парол env'дан, bcrypt hash) - seed'да parol сақламаймиз.
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    username      VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_app_user_username UNIQUE (username)
);
--rollback DROP TABLE app_user;
