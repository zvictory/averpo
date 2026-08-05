--liquibase formatted sql

--changeset averpo:032-01-tax-rate
-- ҚҚС каталоги (docs/modules/tax.md). Ставка ЎЧИРИЛМАЙДИ - active=false;
-- ставка ҚИЙМАТИ ҳужжат сатрига snapshot сақланади (кейин каталогда
-- ўзгарса тарихий ҳужжат бузилмайди). rate - фоиз (12 = 12%),
-- CHECK 0..100 (BR-TAX-002 DB даражасида ҳам).
CREATE TABLE tax_rate (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    rate       NUMERIC(9,4) NOT NULL CHECK (rate >= 0 AND rate <= 100),
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_tax_rate_code UNIQUE (code)
);
--rollback DROP TABLE tax_rate;

--changeset averpo:032-02-tax-rate-seed
-- Ўзбекистон MVP: ҚҚС 12% ва ҚҚСсиз. Id'лар олдиндан генерацияланган
-- UUIDv7 (вақт тартиби сақланади).
INSERT INTO tax_rate (id, code, name, rate, active) VALUES
    ('019f8a10-0001-7a11-9c01-0000000000a1', 'QQS12', 'ҚҚС 12%', 12, true),
    ('019f8a10-0002-7a22-9c02-0000000000a2', 'NO_TAX', 'ҚҚСсиз', 0, true);
--rollback DELETE FROM tax_rate WHERE code IN ('QQS12','NO_TAX');

--changeset averpo:032-03-item-tax-defaults
-- Item сатрида prefill (UoM default'лари паттерни): null - солиқсиз.
-- FK ЙЎҚ (dimension паттерни, модул мустақиллиги - income_account_id
-- каби); tax_rate ўчирилмагани (active=false) учун из «осилиб» қолмайди.
ALTER TABLE item ADD COLUMN sales_tax_rate_id UUID;
ALTER TABLE item ADD COLUMN purchase_tax_rate_id UUID;
--rollback ALTER TABLE item DROP COLUMN sales_tax_rate_id; ALTER TABLE item DROP COLUMN purchase_tax_rate_id;

--changeset averpo:032-04-bill-tax
-- Bill сарлавҳа: нархлар ҚҚСсиз (exclusive, default) ёки ичида (inclusive).
-- Сатр: танланган ставка + snapshot қиймат + сатр ҚҚСи (ҳужжат валютаси).
-- amount семантикаси САҚЛАНАДИ: НЕТТО (солиқсиз); gross = amount + tax_amount.
ALTER TABLE bill ADD COLUMN amounts_inclusive BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bill_line ADD COLUMN tax_rate_id UUID;
ALTER TABLE bill_line ADD COLUMN tax_rate_value NUMERIC(9,4);
ALTER TABLE bill_line ADD COLUMN tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0;
--rollback ALTER TABLE bill DROP COLUMN amounts_inclusive; ALTER TABLE bill_line DROP COLUMN tax_rate_id; ALTER TABLE bill_line DROP COLUMN tax_rate_value; ALTER TABLE bill_line DROP COLUMN tax_amount;

--changeset averpo:032-05-invoice-tax
-- Invoice кўзгуси: сарлавҳа режими + сатр ставка snapshot + сатр ҚҚСи.
ALTER TABLE invoice ADD COLUMN amounts_inclusive BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE invoice_line ADD COLUMN tax_rate_id UUID;
ALTER TABLE invoice_line ADD COLUMN tax_rate_value NUMERIC(9,4);
ALTER TABLE invoice_line ADD COLUMN tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0;
--rollback ALTER TABLE invoice DROP COLUMN amounts_inclusive; ALTER TABLE invoice_line DROP COLUMN tax_rate_id; ALTER TABLE invoice_line DROP COLUMN tax_rate_value; ALTER TABLE invoice_line DROP COLUMN tax_amount;
