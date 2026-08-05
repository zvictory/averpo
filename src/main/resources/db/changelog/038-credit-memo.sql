--liquibase formatted sql

--changeset averpo:038-01-credit-memo
-- Мижоз кредит-нотаси (docs/modules/returns.md, QBO CreditMemo 10497):
-- invoice сарлавҳа қолипи, DRAFT йўқ - яратилди = POSTED (bank txn
-- нақши), тузатиш reverse. invoice_id - ихтиёрий асл ҳужжат ҳаволаси
-- (prefill + қайтим таннархи асл сотувдан). applied_amount/open_balance -
-- денормализация (invoice paid/balance қолипи).
CREATE TABLE credit_memo (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    cm_number         VARCHAR(20) NOT NULL,
    customer_id       UUID NOT NULL REFERENCES contact(id),
    invoice_id        UUID REFERENCES invoice(id),
    cm_date           DATE NOT NULL,
    currency_id       UUID NOT NULL REFERENCES currency(id),
    exchange_rate     NUMERIC(24,12) NOT NULL,
    amounts_inclusive BOOLEAN NOT NULL DEFAULT false,
    total             NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_base        NUMERIC(19,4) NOT NULL DEFAULT 0,
    applied_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    open_balance      NUMERIC(19,4) NOT NULL DEFAULT 0,
    status            VARCHAR(10) NOT NULL,
    posted_at         TIMESTAMPTZ,
    memo              VARCHAR(500),
    CONSTRAINT uq_credit_memo_number UNIQUE (cm_number)
);
--rollback DROP TABLE credit_memo;

--changeset averpo:038-02-credit-memo-line
-- Сатрлар invoice_line кўзгуси (net/tax snapshot - tax.md механизми
-- айнан) + class_id (class-tracking.md - даромад/COGS легига кўчади).
-- (owner, line_no) UNIQUE - PERF-010 қолипи.
CREATE TABLE credit_memo_line (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    credit_memo_id    UUID NOT NULL REFERENCES credit_memo(id),
    line_no           INT NOT NULL,
    line_type         VARCHAR(10) NOT NULL,
    item_id           UUID NOT NULL REFERENCES item(id),
    warehouse_id      UUID REFERENCES warehouse(id),
    quantity          NUMERIC(19,4) NOT NULL,
    unit_price        NUMERIC(19,4) NOT NULL,
    unit_id           UUID REFERENCES unit(id),
    unit_factor       NUMERIC(19,6),
    income_account_id UUID NOT NULL REFERENCES account(id),
    amount            NUMERIC(19,4) NOT NULL,
    tax_rate_id       UUID,
    tax_rate_value    NUMERIC(9,4),
    tax_amount        NUMERIC(19,4) NOT NULL DEFAULT 0,
    class_id          UUID REFERENCES txn_class(id),
    memo              VARCHAR(500),
    CONSTRAINT uq_credit_memo_line_no UNIQUE (credit_memo_id, line_no)
);
--rollback DROP TABLE credit_memo_line;

--changeset averpo:038-03-credit-application
-- Кредитни invoice'га қўллаш - payment allocation қолипи: GL'сиз
-- subledger ҳаракати (фақат FX фарқи алоҳида JE, CREDIT_APPLICATION
-- манба). Бир (кредит, invoice) жуфтига биттагина ёзув.
CREATE TABLE credit_application (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    credit_memo_id UUID NOT NULL REFERENCES credit_memo(id),
    invoice_id     UUID NOT NULL REFERENCES invoice(id),
    amount         NUMERIC(19,4) NOT NULL,
    CONSTRAINT uq_credit_application UNIQUE (credit_memo_id, invoice_id)
);
--rollback DROP TABLE credit_application;

--changeset averpo:038-04-credit-memo-sequence
-- CM-2026-NNNNN рақамлаш қатори (DocumentType.CREDIT_MEMO). Id -
-- олдиндан генерацияланган UUIDv7 (014-02 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f8e40-3a71-7d24-9b52-c48f1e07a3d5', 'CREDIT_MEMO', 'CM', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'CREDIT_MEMO';
