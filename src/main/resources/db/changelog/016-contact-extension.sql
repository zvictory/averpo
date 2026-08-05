--liquibase formatted sql

--changeset averpo:016-01-contact-tax-credit
-- Contact кенгайтмаси (docs/modules/contact.md, old-erp-ideas §3):
-- ИНН partial unique (NULL дубликат муаммосини ечади), credit limit
-- фақат customer учун - тип текшируви service'да (BR-CON-006).
ALTER TABLE contact ADD COLUMN tax_id VARCHAR(20);
ALTER TABLE contact ADD COLUMN credit_limit NUMERIC(19,4)
    CHECK (credit_limit IS NULL OR credit_limit >= 0);
CREATE UNIQUE INDEX ux_contact_tax_id ON contact(tax_id) WHERE tax_id IS NOT NULL;
--rollback DROP INDEX ux_contact_tax_id; ALTER TABLE contact DROP COLUMN tax_id; ALTER TABLE contact DROP COLUMN credit_limit;

--changeset averpo:016-02-contact-address
-- Структурали манзиллар. Ҳар (contact, type)да биттагина default -
-- partial unique DB даражасида кафолатлайди, service янги default
-- келганда эскисини бўшатади.
CREATE TABLE contact_address (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    contact_id    UUID NOT NULL REFERENCES contact(id),
    address_type  VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(500) NOT NULL,
    address_line2 VARCHAR(200),
    city          VARCHAR(100),
    region        VARCHAR(100),
    postal_code   VARCHAR(20),
    country_code  VARCHAR(2),
    is_default    BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_contact_address_contact ON contact_address(contact_id);
CREATE UNIQUE INDEX ux_contact_address_default
    ON contact_address(contact_id, address_type) WHERE is_default;
--rollback DROP TABLE contact_address;

--changeset averpo:016-03-contact-person
-- Контакт шахслари. Контактда биттагина primary - partial unique.
CREATE TABLE contact_person (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    contact_id UUID NOT NULL REFERENCES contact(id),
    full_name  VARCHAR(200) NOT NULL,
    position   VARCHAR(100),
    phone      VARCHAR(50),
    email      VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_contact_person_contact ON contact_person(contact_id);
CREATE UNIQUE INDEX ux_contact_person_primary
    ON contact_person(contact_id) WHERE is_primary;
--rollback DROP TABLE contact_person;

--changeset averpo:016-04-contact-bank-account
-- Банк реквизитлари. Ҳисоб рақами контакт ичида unique (BR-CON-010);
-- эски лойиҳадаги status enum атайлаб олинмади (соддалаштириш).
CREATE TABLE contact_bank_account (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    contact_id     UUID NOT NULL REFERENCES contact(id),
    bank_name      VARCHAR(200) NOT NULL,
    bank_code      VARCHAR(20),
    account_number VARCHAR(50) NOT NULL,
    currency_id    UUID REFERENCES currency(id),
    is_default     BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_contact_bank_account UNIQUE (contact_id, account_number)
);
CREATE INDEX idx_contact_bank_contact ON contact_bank_account(contact_id);
CREATE UNIQUE INDEX ux_contact_bank_default
    ON contact_bank_account(contact_id) WHERE is_default;
--rollback DROP TABLE contact_bank_account;

--changeset averpo:016-05-contact-address-migrate
-- Эски оддий матн манзиллар структурага кўчади (1-қаторга, default
-- сифатида), кейин устунлар олиб ташланади. uuidv7() - PostgreSQL 18
-- built-in, id'лар UUIDv7 конвенцияси сақланади.
INSERT INTO contact_address (id, contact_id, address_type, address_line1, is_default)
SELECT uuidv7(), id, 'BILLING', left(btrim(billing_address), 500), true
FROM contact WHERE billing_address IS NOT NULL AND btrim(billing_address) <> '';
INSERT INTO contact_address (id, contact_id, address_type, address_line1, is_default)
SELECT uuidv7(), id, 'SHIPPING', left(btrim(shipping_address), 500), true
FROM contact WHERE shipping_address IS NOT NULL AND btrim(shipping_address) <> '';
ALTER TABLE contact DROP COLUMN billing_address;
ALTER TABLE contact DROP COLUMN shipping_address;
--rollback ALTER TABLE contact ADD COLUMN billing_address TEXT; ALTER TABLE contact ADD COLUMN shipping_address TEXT;
--rollback UPDATE contact c SET billing_address = a.address_line1 FROM contact_address a WHERE a.contact_id = c.id AND a.address_type = 'BILLING' AND a.is_default;
--rollback UPDATE contact c SET shipping_address = a.address_line1 FROM contact_address a WHERE a.contact_id = c.id AND a.address_type = 'SHIPPING' AND a.is_default;
--rollback DELETE FROM contact_address WHERE address_type IN ('BILLING','SHIPPING') AND is_default;
