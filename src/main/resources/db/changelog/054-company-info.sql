--liquibase formatted sql

--changeset averpo:054-company-info
-- Компания реквизит майдонлари (DEC-112, docs/modules/company-info.md):
-- QBO Company Settings + Ўзбекистон реквизитлари (СТИР/банк/директор -
-- ҳужжат/чоп сарлавҳаси учун). Ҳаммаси nullable/ихтиёрий, `name`
-- аллақачон бор. Соф кўрсатиш маълумоти - GL/posting'га таъсир қилмайди.
-- logo_attachment_id → attachment (лого: 101 аватар билан бир хил
-- Attachment инфраси, disk storage, ON DELETE SET NULL). document-print
-- (29) шу майдонларни чоп сарлавҳасида ЎҚИЙДИ - такрор changeset ЙЎҚ.
ALTER TABLE company_settings ADD COLUMN legal_name VARCHAR(255);
ALTER TABLE company_settings ADD COLUMN address VARCHAR(1000);
ALTER TABLE company_settings ADD COLUMN phone VARCHAR(50);
ALTER TABLE company_settings ADD COLUMN email VARCHAR(255);
ALTER TABLE company_settings ADD COLUMN website VARCHAR(255);
ALTER TABLE company_settings ADD COLUMN tax_id VARCHAR(50);
ALTER TABLE company_settings ADD COLUMN bank_name VARCHAR(255);
ALTER TABLE company_settings ADD COLUMN bank_account VARCHAR(50);
ALTER TABLE company_settings ADD COLUMN bank_mfo VARCHAR(20);
ALTER TABLE company_settings ADD COLUMN director_name VARCHAR(255);
ALTER TABLE company_settings ADD COLUMN director_position VARCHAR(255);
ALTER TABLE company_settings ADD COLUMN logo_attachment_id UUID;
ALTER TABLE company_settings ADD CONSTRAINT fk_company_logo
    FOREIGN KEY (logo_attachment_id) REFERENCES attachment (id) ON DELETE SET NULL;
--rollback ALTER TABLE company_settings DROP CONSTRAINT fk_company_logo;
--rollback ALTER TABLE company_settings DROP COLUMN logo_attachment_id;
--rollback ALTER TABLE company_settings DROP COLUMN director_position;
--rollback ALTER TABLE company_settings DROP COLUMN director_name;
--rollback ALTER TABLE company_settings DROP COLUMN bank_mfo;
--rollback ALTER TABLE company_settings DROP COLUMN bank_account;
--rollback ALTER TABLE company_settings DROP COLUMN bank_name;
--rollback ALTER TABLE company_settings DROP COLUMN tax_id;
--rollback ALTER TABLE company_settings DROP COLUMN website;
--rollback ALTER TABLE company_settings DROP COLUMN email;
--rollback ALTER TABLE company_settings DROP COLUMN phone;
--rollback ALTER TABLE company_settings DROP COLUMN address;
--rollback ALTER TABLE company_settings DROP COLUMN legal_name;
