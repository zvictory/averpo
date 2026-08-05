--liquibase formatted sql

--changeset averpo:021-01-invoice
-- Сотув ҳужжати (docs/modules/sales.md). Bill'нинг кўзгу акси:
-- customer_id - dimension паттерни (DB FK, JPA'да UUID). Bill'даги
-- vendor_invoice_number guard'ининг кераги йўқ - invoice_number
-- ўзимизники (unique). Денормализация устунлари рўйхат экранлари учун.
CREATE TABLE invoice (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    invoice_number VARCHAR(20) NOT NULL,
    customer_id    UUID NOT NULL REFERENCES contact(id),
    invoice_date   DATE NOT NULL,
    due_date       DATE,
    currency_id    UUID NOT NULL REFERENCES currency(id),
    exchange_rate  NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    status         VARCHAR(10) NOT NULL,
    total          NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    total_base     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total_base >= 0),
    paid_amount    NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    balance_due    NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (balance_due >= 0),
    payment_status VARCHAR(10) NOT NULL DEFAULT 'UNPAID',
    memo           VARCHAR(500),
    posted_at      TIMESTAMPTZ,
    CONSTRAINT uq_invoice_number UNIQUE (invoice_number)
);
CREATE INDEX idx_invoice_customer ON invoice(customer_id);
CREATE INDEX idx_invoice_status ON invoice(status);
CREATE INDEX idx_invoice_date ON invoice(invoice_date);
--rollback DROP TABLE invoice;

--changeset averpo:021-02-invoice-line
-- Invoice сатри: ITEM (INVENTORY item - омбордан чиқим + COGS) ёки
-- SERVICE (SERVICE/NON_INVENTORY item - омборсиз, фақат даромад).
-- Ҳар икки турда item танланади (item_id NOT NULL); даромад счёти
-- item'дан default олинади, сатрда ўзгартириш мумкин.
CREATE TABLE invoice_line (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    invoice_id        UUID NOT NULL REFERENCES invoice(id),
    line_no           INT NOT NULL,
    type              VARCHAR(10) NOT NULL,
    item_id           UUID NOT NULL REFERENCES item(id),
    warehouse_id      UUID REFERENCES warehouse(id),
    quantity          NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price        NUMERIC(24,12) NOT NULL CHECK (unit_price >= 0),
    income_account_id UUID NOT NULL REFERENCES account(id),
    amount            NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    memo              VARCHAR(500)
);
CREATE INDEX idx_invoice_line_invoice ON invoice_line(invoice_id);
CREATE INDEX idx_invoice_line_item ON invoice_line(item_id);
--rollback DROP TABLE invoice_line;

--changeset averpo:021-03-invoice-payment
-- Мижоз тўлови/тушум: DRAFT'сиз (яратилди = POSTED). Аванс рухсат -
-- total/allocated/unallocated денормализацияси (BillPayment кўзгуси).
-- deposit_account_id - банк/касса/UNDEPOSITED_FUNDS (BR-RCPT-002).
CREATE TABLE invoice_payment (
    id                 UUID PRIMARY KEY,
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    receipt_number     VARCHAR(20) NOT NULL,
    customer_id        UUID NOT NULL REFERENCES contact(id),
    payment_date       DATE NOT NULL,
    deposit_account_id UUID NOT NULL REFERENCES account(id),
    currency_id        UUID NOT NULL REFERENCES currency(id),
    exchange_rate      NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    total_amount       NUMERIC(19,4) NOT NULL CHECK (total_amount > 0),
    allocated_amount   NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (allocated_amount >= 0),
    unallocated_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (unallocated_amount >= 0),
    status             VARCHAR(10) NOT NULL,
    memo               VARCHAR(500),
    CONSTRAINT uq_invoice_payment_number UNIQUE (receipt_number),
    CONSTRAINT ck_invoice_payment_alloc CHECK (allocated_amount + unallocated_amount = total_amount)
);
CREATE INDEX idx_invoice_payment_customer ON invoice_payment(customer_id);
CREATE INDEX idx_invoice_payment_date ON invoice_payment(payment_date);
--rollback DROP TABLE invoice_payment;

--changeset averpo:021-04-invoice-payment-allocation
-- Тушум тақсимоти: бир тўлов бир нечта invoice'га; бир (тўлов,
-- invoice) жуфтига биттагина ёзув (BR-RCPT-011).
CREATE TABLE invoice_payment_allocation (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payment_id UUID NOT NULL REFERENCES invoice_payment(id),
    invoice_id UUID NOT NULL REFERENCES invoice(id),
    amount     NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_ipa_payment_invoice UNIQUE (payment_id, invoice_id)
);
CREATE INDEX idx_ipa_invoice ON invoice_payment_allocation(invoice_id);
--rollback DROP TABLE invoice_payment_allocation;

--changeset averpo:021-05-receipt-sequence
-- RCPT-2026-NNNNN рақамлаш қатори (DocumentType.RECEIPT). INV сериси
-- 014-02'да аллақачон бор. Id - олдиндан генерацияланган UUIDv7.
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f37c9-4e82-71a5-b4c7-2d90e5f13b64', 'RECEIPT', 'RCPT', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'RECEIPT';
