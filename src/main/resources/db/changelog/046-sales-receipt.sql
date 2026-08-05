--liquibase formatted sql

--changeset averpo:046-01-sales-receipt
-- Сотув чеки (posting-rules «Сотув чеки»): сотув + тўлов бир ҳужжатда,
-- invoice'нинг AR'сиз кўзгуси. DRAFT йўқ - яратилди = POSTED
-- (refund_receipt қолипи), тузатиш reverse. Тўлов дарҳол банк/кассага
-- (bank_account_id), allocation/AR йўқ. total/total_base денормализация.
CREATE TABLE sales_receipt (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    sr_number         VARCHAR(20) NOT NULL,
    customer_id       UUID NOT NULL REFERENCES contact(id),
    bank_account_id   UUID NOT NULL REFERENCES account(id),
    sr_date           DATE NOT NULL,
    currency_id       UUID NOT NULL REFERENCES currency(id),
    exchange_rate     NUMERIC(24,12) NOT NULL,
    amounts_inclusive BOOLEAN NOT NULL DEFAULT false,
    total             NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_base        NUMERIC(19,4) NOT NULL DEFAULT 0,
    status            VARCHAR(10) NOT NULL,
    posted_at         TIMESTAMPTZ,
    memo              VARCHAR(500),
    CONSTRAINT uq_sales_receipt_number UNIQUE (sr_number)
);
--rollback DROP TABLE sales_receipt;

--changeset averpo:046-02-sales-receipt-line
-- Сатрлар invoice_line/refund_receipt_line кўзгуси: ITEM/SERVICE,
-- item/qty/нарх, UoM snapshot (unit_id/unit_factor), ҚҚС snapshot
-- (tax_rate_id/value; amount НЕТТО, gross = amount + tax_amount),
-- class_id. ITEM сатрда омбор чиқими (warehouse_id). (owner, line_no) UNIQUE.
CREATE TABLE sales_receipt_line (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    sales_receipt_id  UUID NOT NULL REFERENCES sales_receipt(id),
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
    CONSTRAINT uq_sales_receipt_line_no UNIQUE (sales_receipt_id, line_no)
);
--rollback DROP TABLE sales_receipt_line;

--changeset averpo:046-03-sales-receipt-sequence
-- SR-2026-NNNNN рақамлаш қатори (DocumentType SALES_RECEIPT). Id
-- олдиндан генерацияланган UUIDv7 (014-02/041-06 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f4c46-0001-7461-8a46-0d1e2f3a4b5c', 'SALES_RECEIPT', 'SR', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'SALES_RECEIPT';
