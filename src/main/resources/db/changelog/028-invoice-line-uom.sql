--liquibase formatted sql

--changeset averpo:028-01-invoice-line-uom
-- Invoice сатрида киритилган бирлик + factor SNAPSHOT (docs/modules/
-- uom.md, 027-bill-line-uom кўзгуси): миқдор/нарх киритилган бирликда,
-- омбордан чиқим base миқдорда (qty × unit_factor). NULL - item base
-- бирлиги (эски сатрлар), factor 1 деб ўқилади.
ALTER TABLE invoice_line ADD COLUMN unit_id UUID REFERENCES unit(id);
ALTER TABLE invoice_line ADD COLUMN unit_factor NUMERIC(24,12)
    CHECK (unit_factor IS NULL OR unit_factor > 0);
--rollback ALTER TABLE invoice_line DROP COLUMN unit_id, DROP COLUMN unit_factor;
