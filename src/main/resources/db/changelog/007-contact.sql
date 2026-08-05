--liquibase formatted sql

--changeset averpo:010-payment-term
-- QBO Terms рўйхати — контакт ва (кейин) Invoice/Bill ишлатади
CREATE TABLE payment_term (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    name       VARCHAR(100) NOT NULL,
    days       INT NOT NULL CHECK (days >= 0),
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_payment_term_name UNIQUE (name)
);
--rollback DROP TABLE payment_term;

--changeset averpo:011-payment-term-seed
-- Id'лар олдиндан генерацияланган UUIDv7
INSERT INTO payment_term (id, name, days, active) VALUES
    ('019f337a-8364-7af7-b6f9-cdbb2205cb60', 'Due on receipt', 0, true),
    ('019f337a-840a-7911-b618-ead606bae900', 'Net 15', 15, true),
    ('019f337a-840a-731d-9e61-c977d33ee0fc', 'Net 30', 30, true),
    ('019f337a-840a-78b7-900f-1eaab7041c0c', 'Net 60', 60, true);
--rollback DELETE FROM payment_term WHERE name IN ('Due on receipt','Net 15','Net 30','Net 60');

--changeset averpo:012-contact
CREATE TABLE contact (
    id               UUID PRIMARY KEY,
    version          INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    type             VARCHAR(10) NOT NULL,
    display_name     VARCHAR(255) NOT NULL,
    company_name     VARCHAR(255),
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    email            VARCHAR(255),
    phone            VARCHAR(50),
    currency         VARCHAR(3),
    payment_term_id  UUID REFERENCES payment_term(id),
    billing_address  TEXT,
    shipping_address TEXT,
    notes            TEXT,
    active           BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_contact_display_name UNIQUE (display_name)
);
CREATE INDEX idx_contact_type ON contact(type);
--rollback DROP TABLE contact;
