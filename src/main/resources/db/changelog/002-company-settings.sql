--liquibase formatted sql

--changeset averpo:002-company-settings
CREATE TABLE company_settings (
    id              UUID PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    name            VARCHAR(255) NOT NULL,
    home_currency   VARCHAR(3) NOT NULL,
    timezone        VARCHAR(50) NOT NULL,
    singleton_guard BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_company_settings_singleton UNIQUE (singleton_guard),
    CONSTRAINT ck_company_settings_singleton CHECK (singleton_guard = true)
);
--rollback DROP TABLE company_settings;
