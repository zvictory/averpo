--liquibase formatted sql

--changeset averpo:019-01-bill
-- Харид ҳужжати (docs/modules/purchases.md). vendor_id - dimension
-- паттерни (DB FK бор, JPA'да оддий UUID). Денормализация устунлари
-- (paid_amount, balance_due, payment_status) рўйхат экранлари тез
-- бўлиши учун (old-erp-ideas §4).
CREATE TABLE bill (
    id                    UUID PRIMARY KEY,
    version               INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    bill_number           VARCHAR(20) NOT NULL,
    vendor_id             UUID NOT NULL REFERENCES contact(id),
    vendor_invoice_number VARCHAR(100),
    bill_date             DATE NOT NULL,
    due_date              DATE,
    currency_id           UUID NOT NULL REFERENCES currency(id),
    exchange_rate         NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    status                VARCHAR(10) NOT NULL,
    total                 NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    total_base            NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total_base >= 0),
    paid_amount           NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    balance_due           NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (balance_due >= 0),
    payment_status        VARCHAR(10) NOT NULL DEFAULT 'UNPAID',
    memo                  VARCHAR(500),
    posted_at             TIMESTAMPTZ,
    CONSTRAINT uq_bill_number UNIQUE (bill_number)
);
CREATE INDEX idx_bill_vendor ON bill(vendor_id);
CREATE INDEX idx_bill_status ON bill(status);
CREATE INDEX idx_bill_date ON bill(bill_date);
-- Vendor duplicate guard (BR-BILL-006): REVERSED'дан кейин ўша рақам
-- билан қайта киритиш очиқ - shuning учун status шарти билан partial
CREATE UNIQUE INDEX ux_bill_vendor_invoice ON bill(vendor_id, vendor_invoice_number)
    WHERE vendor_invoice_number IS NOT NULL AND status IN ('DRAFT', 'POSTED');
--rollback DROP TABLE bill;

--changeset averpo:019-02-bill-line
-- Bill сатри: ITEM (омбор кирими) / EXPENSE (харажат счёти) /
-- LANDED_COST (клирингга). Суммалар ҳужжат валютасида.
CREATE TABLE bill_line (
    id           UUID PRIMARY KEY,
    version      INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    bill_id      UUID NOT NULL REFERENCES bill(id),
    line_no      INT NOT NULL,
    type         VARCHAR(15) NOT NULL,
    item_id      UUID REFERENCES item(id),
    warehouse_id UUID REFERENCES warehouse(id),
    quantity     NUMERIC(19,4) CHECK (quantity IS NULL OR quantity > 0),
    unit_price   NUMERIC(24,12) CHECK (unit_price IS NULL OR unit_price >= 0),
    account_id   UUID REFERENCES account(id),
    amount       NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    memo         VARCHAR(500)
);
CREATE INDEX idx_bill_line_bill ON bill_line(bill_id);
CREATE INDEX idx_bill_line_item ON bill_line(item_id);
--rollback DROP TABLE bill_line;

--changeset averpo:019-03-bill-payment
-- Vendor тўлови: DRAFT'сиз (яратилди = POSTED). Аванс рухсат -
-- total/allocated/unallocated денормализацияси (old-erp-ideas §4).
CREATE TABLE bill_payment (
    id                 UUID PRIMARY KEY,
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    payment_number     VARCHAR(20) NOT NULL,
    vendor_id          UUID NOT NULL REFERENCES contact(id),
    payment_date       DATE NOT NULL,
    bank_account_id    UUID NOT NULL REFERENCES account(id),
    currency_id        UUID NOT NULL REFERENCES currency(id),
    exchange_rate      NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    total_amount       NUMERIC(19,4) NOT NULL CHECK (total_amount > 0),
    allocated_amount   NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (allocated_amount >= 0),
    unallocated_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (unallocated_amount >= 0),
    status             VARCHAR(10) NOT NULL,
    memo               VARCHAR(500),
    CONSTRAINT uq_bill_payment_number UNIQUE (payment_number),
    CONSTRAINT ck_bill_payment_alloc CHECK (allocated_amount + unallocated_amount = total_amount)
);
CREATE INDEX idx_bill_payment_vendor ON bill_payment(vendor_id);
CREATE INDEX idx_bill_payment_date ON bill_payment(payment_date);
--rollback DROP TABLE bill_payment;

--changeset averpo:019-04-bill-payment-allocation
-- Тўлов тақсимоти: бир тўлов бир нечта bill'га; бир (тўлов, bill)
-- жуфтига биттагина ёзув. Полиморф ҳавола АТАЙЛАБ йўқ - тўлов фақат
-- bill'ларга (purchase ичида, 2026-07-06 қарори).
CREATE TABLE bill_payment_allocation (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payment_id UUID NOT NULL REFERENCES bill_payment(id),
    bill_id    UUID NOT NULL REFERENCES bill(id),
    amount     NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_bpa_payment_bill UNIQUE (payment_id, bill_id)
);
CREATE INDEX idx_bpa_bill ON bill_payment_allocation(bill_id);
--rollback DROP TABLE bill_payment_allocation;
