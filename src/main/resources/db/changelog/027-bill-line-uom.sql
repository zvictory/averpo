--liquibase formatted sql

--changeset averpo:027-01-bill-line-uom
-- Bill сатрида киритилган бирлик + factor SNAPSHOT (docs/modules/uom.md):
-- миқдор/нарх киритилган бирликда қолади, омборга base миқдор
-- (qty × unit_factor) боради. Snapshot - кейин каталогда factor
-- ўзгарса тарихий ҳужжат бузилмасин (Money.exchangeRate услуби).
-- NULL - item base бирлиги (эски сатрлар), factor 1 деб ўқилади.
ALTER TABLE bill_line ADD COLUMN unit_id UUID REFERENCES unit(id);
ALTER TABLE bill_line ADD COLUMN unit_factor NUMERIC(24,12)
    CHECK (unit_factor IS NULL OR unit_factor > 0);
--rollback ALTER TABLE bill_line DROP COLUMN unit_id, DROP COLUMN unit_factor;
