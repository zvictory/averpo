--liquibase formatted sql

--changeset averpo:048-01-company-settings-setup-done
-- DEC-056: онбординг флаги. Янги (бўш) ўрнатишда CompanySettings
-- жимгина default билан яратилади ('Компания', UZS) - фойдаланувчи
-- созлаш кераклигини билмай дашбордга тушиб қолади. Явный флаг керак:
-- name'ни 'Компания'га солиштириш мўрт (ном ростдан шундай бўлиши мумкин).
-- Login success handler ADMIN'ни setup_done=false бўлса /settings?setup=1
-- га йўналтиради.
ALTER TABLE company_settings ADD COLUMN setup_done BOOLEAN NOT NULL DEFAULT false;
-- Backfill: мавжуд созланган ўрнатишлар (ном default'дан фарқли) қайта
-- сўралмасин - улар аллақачон ишлаётган базалар, онбординг ортда қолган.
UPDATE company_settings SET setup_done = true WHERE name <> 'Компания';
--rollback ALTER TABLE company_settings DROP COLUMN setup_done;
