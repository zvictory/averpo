--liquibase formatted sql

--changeset erp:001-exchange-rate
CREATE TABLE exchange_rate (
    id          UUID PRIMARY KEY,
    version     INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    currency    VARCHAR(3) NOT NULL,
    rate_date   DATE NOT NULL,
    rate        NUMERIC(19,8) NOT NULL CHECK (rate > 0),
    CONSTRAINT uq_exchange_rate UNIQUE (currency, rate_date)
);
--rollback DROP TABLE exchange_rate;
