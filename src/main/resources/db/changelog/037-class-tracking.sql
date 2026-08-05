--liquibase formatted sql

--changeset averpo:037-01-txn-class
-- Class tracking каталоги (docs/modules/class-tracking.md, QBO Class
-- 8849): даромад/харажатни йўналиш кесимида кузатиш - GL суммаларига
-- таъсир қилмайдиган соф таҳлилий тег. Жадвал номи txn_class - «class»
-- Java reserved сўзи. Ўчириш ЙЎҚ - active=false (GL тарихида
-- ишлатилган бўлиши мумкин). UNIQUE NULLS NOT DISTINCT - top-level
-- (parent_id NULL) номлар ҳам ноёб (PostgreSQL 18, BR-CLS-002).
CREATE TABLE txn_class (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    name       VARCHAR(100) NOT NULL,
    parent_id  UUID REFERENCES txn_class(id),
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_txn_class_parent_name UNIQUE NULLS NOT DISTINCT (parent_id, name)
);
--rollback DROP TABLE txn_class;

--changeset averpo:037-02-company-settings-track-classes
-- Режим (QBO Preferences ClassTrackingPerTxn/PerTxnLine): OFF (default) /
-- PER_TXN / PER_LINE. Қулфланмайди - исталган пайт алмаштирилади, эски
-- ҳужжатлар ўзгармайди. Режим фақат UI'ни бошқаради - схема ягона
-- (class ҳар доим САТРДА).
ALTER TABLE company_settings ADD COLUMN track_classes VARCHAR(10) NOT NULL DEFAULT 'OFF';
--rollback ALTER TABLE company_settings DROP COLUMN track_classes;

--changeset averpo:037-03-line-class-columns
--validCheckSum: 9:b3a110c689899c7486ecd906fed81cc3
-- (validCheckSum - DEC-077: танадаги «Finance.xsd'да ClassRef йўқ»
-- изоҳи ЯЛҒОН эди, тузатилди; SQL ўзгармаган, эски checksum шу
-- директива билан оқланади - қўлланган базалар validation'дан ўтади.)
-- Сатр даражасидаги тег: GL сатри (ҳисобот шу устундан ўқийди - индекс)
-- ва ҳужжат сатрлари. Backfill ЙЎҚ - эски сатрлар «Кўрсатилмаган»
-- устунига тушади (QBO услуби). Transfer/payment сатрларига class
-- йўқ - ClassRef Finance.xsd'да БОР (:10316), лекин Averpo class
-- кўлами P&L-only бўлгани учун онгли қўлланмайди (class-tracking.md).
ALTER TABLE journal_entry_line ADD COLUMN class_id UUID REFERENCES txn_class(id);
CREATE INDEX idx_jel_class ON journal_entry_line (class_id) WHERE class_id IS NOT NULL;
ALTER TABLE invoice_line ADD COLUMN class_id UUID REFERENCES txn_class(id);
ALTER TABLE bill_line ADD COLUMN class_id UUID REFERENCES txn_class(id);
ALTER TABLE bank_transaction_line ADD COLUMN class_id UUID REFERENCES txn_class(id);
--rollback ALTER TABLE bank_transaction_line DROP COLUMN class_id; ALTER TABLE bill_line DROP COLUMN class_id; ALTER TABLE invoice_line DROP COLUMN class_id; DROP INDEX idx_jel_class; ALTER TABLE journal_entry_line DROP COLUMN class_id;
