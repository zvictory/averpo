--liquibase formatted sql

--changeset averpo:045-01-payroll-payment
-- Иш ҳақи тўлови (docs/modules/payroll.md 23в; posting-rules «Иш ҳақи»):
-- аванс/ойлик тўлови - Dr PAYROLL_CLEARING (ҳар ходим кесимида) / Cr
-- банк-касса. Ҳамма payroll ҳужжати ФАҚАТ home валютада (BR-PYR-001) -
-- валюта/курс устуни ЙЎҚ (base == amount). Ҳаёт цикли invoice қолипи:
-- DRAFT (таҳрир/ўчириш) → POSTED (ўзгармас) → REVERSED. payment_type -
-- фақат белги (ADVANCE/SALARY, ведомость/рўйхат учун), проводкаси бир хил.
-- total - денормализация (сатрлар йиғиндиси, рўйхат экрани учун).
CREATE TABLE payroll_payment (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    payp_number    VARCHAR(20) NOT NULL,
    payment_type   VARCHAR(10) NOT NULL,
    payment_date   DATE NOT NULL,
    account_id     UUID NOT NULL REFERENCES account(id),
    total          NUMERIC(19,4) NOT NULL DEFAULT 0,
    status         VARCHAR(10) NOT NULL,
    posted_at      TIMESTAMPTZ,
    memo           VARCHAR(500),
    CONSTRAINT uq_payroll_payment_number UNIQUE (payp_number)
);
--rollback DROP TABLE payroll_payment;

--changeset averpo:045-02-payroll-payment-line
-- Сатр: ходим (EMPLOYEE, фаол - BR-PYR-003) + сумма (> 0, DB CHECK ҳам).
-- Битта тўловда ходим бир марта (UNIQUE payment_id, employee_id -
-- BR-PYR-003 такрор гарови). employee_id - dimension (FK contact, JPA'да
-- оддий UUID, қоида №6). Home валютада (base == amount).
CREATE TABLE payroll_payment_line (
    id           UUID PRIMARY KEY,
    version      INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    payment_id   UUID NOT NULL REFERENCES payroll_payment(id),
    employee_id  UUID NOT NULL REFERENCES contact(id),
    amount       NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_payroll_payment_line_employee UNIQUE (payment_id, employee_id)
);
--rollback DROP TABLE payroll_payment_line;

--changeset averpo:045-03-payroll-payment-sequence
-- PAYP-2026-NNNNN рақамлаш қатори (DocumentType PAYROLL_PAYMENT). Id
-- олдиндан генерацияланган UUIDv7 (041-06 услуби). Run рақами PAYR
-- (044, Ғайрат) - алоҳида префикс.
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f4b21-0001-7c01-8d01-0a1b2c3d4f01', 'PAYROLL_PAYMENT', 'PAYP', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'PAYROLL_PAYMENT';
