--liquibase formatted sql

--changeset averpo:041-01-vendor-credit
-- Таъминотчи кредит-нотаси (docs/modules/returns.md, QBO VendorCredit
-- 8723): bill сарлавҳа қолипи, DRAFT йўқ - яратилди = POSTED (038
-- credit_memo кўзгуси), тузатиш reverse. bill_id - ихтиёрий асл ҳужжат
-- ҳаволаси (prefill + BR-RET-006 миқдор чеклови).
-- applied_amount/open_balance - денормализация (credit_memo қолипи).
CREATE TABLE vendor_credit (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    vc_number         VARCHAR(20) NOT NULL,
    vendor_id         UUID NOT NULL REFERENCES contact(id),
    bill_id           UUID REFERENCES bill(id),
    vc_date           DATE NOT NULL,
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
    CONSTRAINT uq_vendor_credit_number UNIQUE (vc_number)
);
--rollback DROP TABLE vendor_credit;

--changeset averpo:041-02-vendor-credit-line
-- Сатрлар bill_line кўзгуси (ITEM/EXPENSE; net/tax snapshot - tax.md
-- механизми айнан) + class_id (class-tracking.md). ITEM'да item/омбор,
-- EXPENSE'да account_id тўлдирилади. (owner, line_no) UNIQUE -
-- Beruniy-010 қолипи.
CREATE TABLE vendor_credit_line (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    vendor_credit_id  UUID NOT NULL REFERENCES vendor_credit(id),
    line_no           INT NOT NULL,
    line_type         VARCHAR(10) NOT NULL,
    item_id           UUID REFERENCES item(id),
    warehouse_id      UUID REFERENCES warehouse(id),
    quantity          NUMERIC(19,4),
    unit_price        NUMERIC(19,4),
    unit_id           UUID REFERENCES unit(id),
    unit_factor       NUMERIC(19,6),
    account_id        UUID REFERENCES account(id),
    amount            NUMERIC(19,4) NOT NULL,
    tax_rate_id       UUID,
    tax_rate_value    NUMERIC(9,4),
    tax_amount        NUMERIC(19,4) NOT NULL DEFAULT 0,
    class_id          UUID REFERENCES txn_class(id),
    memo              VARCHAR(500),
    CONSTRAINT uq_vendor_credit_line_no UNIQUE (vendor_credit_id, line_no)
);
--rollback DROP TABLE vendor_credit_line;

--changeset averpo:041-03-vendor-credit-application
-- Кредитни bill'га қўллаш - credit_application кўзгуси (AP томони):
-- GL'сиз subledger ҳаракати (фақат FX фарқи алоҳида JE,
-- VENDOR_CREDIT_APPLICATION манба). Бир (кредит, bill) жуфтига
-- биттагина ёзув.
CREATE TABLE vendor_credit_application (
    id               UUID PRIMARY KEY,
    version          INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    vendor_credit_id UUID NOT NULL REFERENCES vendor_credit(id),
    bill_id          UUID NOT NULL REFERENCES bill(id),
    amount           NUMERIC(19,4) NOT NULL,
    CONSTRAINT uq_vendor_credit_application UNIQUE (vendor_credit_id, bill_id)
);
--rollback DROP TABLE vendor_credit_application;

--changeset averpo:041-04-refund-receipt
-- Мижозга пул қайтариш чеки (QBO RefundReceipt 10537): credit_memo
-- кўзгуси, фарқи - AR ўрнига пул счёти (bank_account_id, ҳужжатда
-- танланади) ва application ЙЎҚ (тугал ҳужжат - applied/open устунлар
-- ҳам йўқ). invoice_id - ихтиёрий ҳавола (қайтим таннархи асл
-- сотувдан, posting-rules «Inventory қайтим таннархи»).
CREATE TABLE refund_receipt (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    rr_number         VARCHAR(20) NOT NULL,
    customer_id       UUID NOT NULL REFERENCES contact(id),
    invoice_id        UUID REFERENCES invoice(id),
    bank_account_id   UUID NOT NULL REFERENCES account(id),
    rr_date           DATE NOT NULL,
    currency_id       UUID NOT NULL REFERENCES currency(id),
    exchange_rate     NUMERIC(24,12) NOT NULL,
    amounts_inclusive BOOLEAN NOT NULL DEFAULT false,
    total             NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_base        NUMERIC(19,4) NOT NULL DEFAULT 0,
    status            VARCHAR(10) NOT NULL,
    posted_at         TIMESTAMPTZ,
    memo              VARCHAR(500),
    CONSTRAINT uq_refund_receipt_number UNIQUE (rr_number)
);
--rollback DROP TABLE refund_receipt;

--changeset averpo:041-05-refund-receipt-line
-- Сатрлар credit_memo_line'нинг айнан кўзгуси (даромад қайтади,
-- ҚҚС snapshot, class_id).
CREATE TABLE refund_receipt_line (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    refund_receipt_id UUID NOT NULL REFERENCES refund_receipt(id),
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
    CONSTRAINT uq_refund_receipt_line_no UNIQUE (refund_receipt_id, line_no)
);
--rollback DROP TABLE refund_receipt_line;

--changeset averpo:041-06-returns-sequences
-- VC-2026-NNNNN ва RR-2026-NNNNN рақамлаш қаторлари (DocumentType
-- VENDOR_CREDIT/REFUND_RECEIPT). Id'лар олдиндан генерацияланган
-- UUIDv7 (014-02 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f3f34-469f-75cb-bcdc-a8dbcbada57f', 'VENDOR_CREDIT', 'VC', true, 5, 1),
       ('019f3f34-46a0-760b-b931-f5dcbcece5a5', 'REFUND_RECEIPT', 'RR', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type IN ('VENDOR_CREDIT', 'REFUND_RECEIPT');
