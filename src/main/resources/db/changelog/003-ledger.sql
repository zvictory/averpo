--liquibase formatted sql

--changeset averpo:003-account
CREATE TABLE account (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    name           VARCHAR(255) NOT NULL,
    classification VARCHAR(20) NOT NULL,
    type           VARCHAR(30) NOT NULL,
    detail_type    VARCHAR(40) NOT NULL,
    code           VARCHAR(10),
    description    TEXT,
    parent_id      UUID REFERENCES account(id),
    postable       BOOLEAN NOT NULL DEFAULT true,
    currency       VARCHAR(3),
    active         BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_account_name UNIQUE (name)
);
-- Код ихтиёрий (QBO услуби), лекин киритилса unique
CREATE UNIQUE INDEX uq_account_code ON account(code) WHERE code IS NOT NULL;
CREATE INDEX idx_account_parent ON account(parent_id);
CREATE INDEX idx_account_detail_type ON account(detail_type);
--rollback DROP TABLE account;

--changeset averpo:004-journal-entry
CREATE SEQUENCE journal_entry_number_seq;
CREATE TABLE journal_entry (
    id                 UUID PRIMARY KEY,
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    entry_number       VARCHAR(20) NOT NULL,
    entry_date         DATE NOT NULL,
    description        TEXT,
    status             VARCHAR(10) NOT NULL,
    source_module      VARCHAR(30),
    source_document_id UUID,
    reversed_by_id     UUID REFERENCES journal_entry(id),
    posted_at          TIMESTAMPTZ,
    CONSTRAINT uq_journal_entry_number UNIQUE (entry_number)
);
CREATE INDEX idx_journal_entry_date ON journal_entry(entry_date);
CREATE INDEX idx_journal_entry_status ON journal_entry(status);
CREATE INDEX idx_journal_entry_source ON journal_entry(source_module, source_document_id);
--rollback DROP TABLE journal_entry; DROP SEQUENCE journal_entry_number_seq;

--changeset averpo:005-journal-entry-line
CREATE TABLE journal_entry_line (
    id                   UUID PRIMARY KEY,
    version              INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    entry_id             UUID NOT NULL REFERENCES journal_entry(id),
    line_no              INT NOT NULL,
    account_id           UUID NOT NULL REFERENCES account(id),
    debit_amount         NUMERIC(19,4),
    debit_currency       VARCHAR(3),
    debit_base_amount    NUMERIC(19,4),
    debit_exchange_rate  NUMERIC(19,8),
    credit_amount        NUMERIC(19,4),
    credit_currency      VARCHAR(3),
    credit_base_amount   NUMERIC(19,4),
    credit_exchange_rate NUMERIC(19,8),
    contact_id           UUID,
    warehouse_id         UUID,
    item_id              UUID,
    memo                 VARCHAR(500)
);
CREATE INDEX idx_jel_entry ON journal_entry_line(entry_id);
CREATE INDEX idx_jel_account ON journal_entry_line(account_id);
--rollback DROP TABLE journal_entry_line;
