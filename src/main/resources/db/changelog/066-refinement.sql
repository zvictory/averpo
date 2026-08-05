--liquibase formatted sql

--changeset averpo:066-refinement
-- Рефайнмент майдонлари (DEC-101/112 фойдаланувчи жонли топилмалари).
-- 054/057 аллақачон ҚЎЛЛАНГАН - тегилмайди, янги майдонлар шу changeset'да.
--
-- (1) app_user.must_change_password - admin парол қўйганда (create) ёки
--     reset қилганда true бўлади; фойдаланувчи биринчи login'дан кейин
--     ЎЗ паролини алмаштиргач false. Оддий версия (banner + login redirect)
--     - auth-security-policy мажбурий-алмаштириш механизмини кейин улашади.
-- (2) company_settings.brand_logo_attachment_id - топбар WHITE-LABEL
--     логоси. Company logo'дан ФАРҚли: логотип ҳужжат/чоп сарлавҳаси учун,
--     бренд логоси UI топбар учун (login'дан кейин ҳар роль кўради).
--     → attachment, ON DELETE SET NULL (лого ўчса топбар синмайди).
ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE company_settings ADD COLUMN brand_logo_attachment_id UUID;
ALTER TABLE company_settings ADD CONSTRAINT fk_company_brand_logo
    FOREIGN KEY (brand_logo_attachment_id) REFERENCES attachment (id) ON DELETE SET NULL;
--rollback ALTER TABLE company_settings DROP CONSTRAINT fk_company_brand_logo;
--rollback ALTER TABLE company_settings DROP COLUMN brand_logo_attachment_id;
--rollback ALTER TABLE app_user DROP COLUMN must_change_password;
