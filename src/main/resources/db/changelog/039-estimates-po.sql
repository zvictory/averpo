--liquibase formatted sql

--changeset averpo:039-01-estimate
-- Estimate - мижозга таклиф/смета (docs/modules/estimates-po.md, QBO
-- Estimate). GL'сиз ҳужжат: journal_entry/stock_movement'га АЛОҚА ЙЎҚ,
-- POSTED тушунчаси йўқ - status (PENDING/ACCEPTED/REJECTED/CLOSED)
-- билан бошқарилади, таҳрирланади. invoice_id - айлантирилгандан
-- кейинги linked ҳужжат (LinkedTxn, Finance.xsd услуби).
CREATE TABLE estimate (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    estimate_number   VARCHAR(20) NOT NULL,
    customer_id       UUID NOT NULL REFERENCES contact(id),
    estimate_date     DATE NOT NULL,
    expiration_date   DATE,
    currency_id       UUID NOT NULL REFERENCES currency(id),
    exchange_rate     NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    amounts_inclusive BOOLEAN NOT NULL DEFAULT false,
    status            VARCHAR(10) NOT NULL,
    total             NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    memo              VARCHAR(500),
    invoice_id        UUID REFERENCES invoice(id),
    CONSTRAINT uq_estimate_number UNIQUE (estimate_number)
);
CREATE INDEX idx_estimate_customer ON estimate(customer_id);
CREATE INDEX idx_estimate_status ON estimate(status);
CREATE INDEX idx_estimate_date ON estimate(estimate_date);
--rollback DROP TABLE estimate;

--changeset averpo:039-02-estimate-line
-- Estimate сатри: invoice_line'нинг GL'сиз кўзгуси - омбор/даромад
-- счёти йўқ (айлантиришда invoice формасида танланади). ҚҚС фақат
-- кўрсатиш учун ҳисобланади (amount - net, tax_amount алоҳида);
-- tax_rate_value - snapshot (tax.md нақши).
CREATE TABLE estimate_line (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    estimate_id    UUID NOT NULL REFERENCES estimate(id),
    line_no        INT NOT NULL,
    item_id        UUID NOT NULL REFERENCES item(id),
    quantity       NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(24,12) NOT NULL CHECK (unit_price >= 0),
    unit_id        UUID REFERENCES unit(id),
    amount         NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    tax_rate_id    UUID REFERENCES tax_rate(id),
    tax_rate_value NUMERIC(9,4),
    tax_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    memo           VARCHAR(500),
    CONSTRAINT uq_estimate_line_no UNIQUE (estimate_id, line_no)
);
CREATE INDEX idx_estimate_line_estimate ON estimate_line(estimate_id);
--rollback DROP TABLE estimate_line;

--changeset averpo:039-03-purchase-order
-- PurchaseOrder - таъминотчига буюртма (QBO PurchaseOrder, POStatus
-- Open/Closed). Estimate'нинг харид томонидаги кўзгуси; bill_id -
-- айлантирилгандан кейинги linked ҳужжат.
CREATE TABLE purchase_order (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    po_number         VARCHAR(20) NOT NULL,
    vendor_id         UUID NOT NULL REFERENCES contact(id),
    po_date           DATE NOT NULL,
    expected_date     DATE,
    currency_id       UUID NOT NULL REFERENCES currency(id),
    exchange_rate     NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    amounts_inclusive BOOLEAN NOT NULL DEFAULT false,
    status            VARCHAR(10) NOT NULL,
    total             NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    memo              VARCHAR(500),
    bill_id           UUID REFERENCES bill(id),
    CONSTRAINT uq_po_number UNIQUE (po_number)
);
CREATE INDEX idx_po_vendor ON purchase_order(vendor_id);
CREATE INDEX idx_po_status ON purchase_order(status);
CREATE INDEX idx_po_date ON purchase_order(po_date);
--rollback DROP TABLE purchase_order;

--changeset averpo:039-04-purchase-order-line
-- PO сатри - estimate_line кўзгуси (item буюртмаси; EXPENSE/LANDED_COST
-- турлари йўқ - улар bill'да, айлантиришда қўшилиши мумкин).
CREATE TABLE purchase_order_line (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    po_id          UUID NOT NULL REFERENCES purchase_order(id),
    line_no        INT NOT NULL,
    item_id        UUID NOT NULL REFERENCES item(id),
    quantity       NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(24,12) NOT NULL CHECK (unit_price >= 0),
    unit_id        UUID REFERENCES unit(id),
    amount         NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    tax_rate_id    UUID REFERENCES tax_rate(id),
    tax_rate_value NUMERIC(9,4),
    tax_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    memo           VARCHAR(500),
    CONSTRAINT uq_po_line_no UNIQUE (po_id, line_no)
);
CREATE INDEX idx_po_line_po ON purchase_order_line(po_id);
--rollback DROP TABLE purchase_order_line;

--changeset averpo:039-05-est-po-sequences
-- EST-2026-NNNNN ва PO-2026-NNNNN рақамлаш қаторлари (DocumentType
-- ESTIMATE/PURCHASE_ORDER). Id - олдиндан генерацияланган UUIDv7.
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number) VALUES
    ('019f5b21-7c44-7d3a-9e11-aa03bd7f2c51', 'ESTIMATE', 'EST', true, 5, 1),
    ('019f5b21-7c44-7d3a-9e11-aa03bd7f2c52', 'PURCHASE_ORDER', 'PO', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type IN ('ESTIMATE','PURCHASE_ORDER');
