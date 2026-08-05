--liquibase formatted sql

--changeset averpo:043-01-contact-monthly-salary
-- EMPLOYEE oklad (payroll.md «Contact кенгайтма»): ойлик иш ҳақи prefill
-- қиймати - мажбурий эмас (NULL), home валютада (BR-PAY-001). Фақат
-- EMPLOYEE турдаги контактда тўлдирилади (BR-CON-011 - service гарови).
ALTER TABLE contact ADD COLUMN monthly_salary NUMERIC(19,4);
--rollback ALTER TABLE contact DROP COLUMN monthly_salary;

--changeset averpo:043-02-company-settings-payroll-rates
-- Payroll ставкалари (payroll.md «Ставкалар CompanySettings»): фоиз,
-- ADMIN /settings'да таҳрирлайди (0..100 - BR-SET-005). DEFAULT мавжуд
-- қаторни backfill қилади; янги қатор Java field default'идан келади
-- (12% / 0.1% / 12%). Snapshot: ставка ўзгарса эски POSTED run ўзгармайди.
ALTER TABLE company_settings ADD COLUMN income_tax_rate NUMERIC(9,4) NOT NULL DEFAULT 12;
ALTER TABLE company_settings ADD COLUMN pension_rate NUMERIC(9,4) NOT NULL DEFAULT 0.1;
ALTER TABLE company_settings ADD COLUMN social_tax_rate NUMERIC(9,4) NOT NULL DEFAULT 12;
--rollback ALTER TABLE company_settings DROP COLUMN income_tax_rate; ALTER TABLE company_settings DROP COLUMN pension_rate; ALTER TABLE company_settings DROP COLUMN social_tax_rate;

--changeset averpo:043-03-payroll-accounts-seed
-- Payroll тизим счётлари (payroll.md - detail type орқали топилади,
-- инвариант 6). Бу МАВЖУД базага «топ-ап»: WHERE EXISTS(account) -
-- фақат чарт аллақачон юкланган базага қўшилади. Бўш база (тест
-- baseline, янги ўрнатиш) шу 4 счётни importDefaultChart() орқали
-- default-chart.csv'дан олади - шунда AccountImportTest'нинг «биринчи
-- импортда skipped==0» инварианти бузилмайди (changeset бўш baseline'га
-- ҳеч нима қўшмайди).
-- Жуфт гаров: PAYROLL_CLEARING / PAYROLL_EXPENSES(1) / PAYROLL_TAX_PAYABLE
-- detail type бўйича, иккинчи харажат счёти ном бўйича - шунда эски CSV
-- номи билан аллақачон seed қилинган базада ходим субледжери дубликат
-- detail type'дан бузилмайди (findSystemAccount ягона фаол postable
-- счёт кутади: PAYROLL_CLEARING/PAYROLL_TAX_PAYABLE битта бўлиши шарт).
-- classification/type айнан AccountDetailType enum mapping'идан.
INSERT INTO account (id, name, classification, type, detail_type, postable, active)
SELECT '019f4b10-1001-7c01-8d01-0a1b2c3d4e01', 'Иш ҳақи бўйича мажбурият', 'LIABILITY', 'OTHER_CURRENT_LIABILITY', 'PAYROLL_CLEARING', true, true
WHERE EXISTS (SELECT 1 FROM account)
  AND NOT EXISTS (SELECT 1 FROM account WHERE detail_type = 'PAYROLL_CLEARING');
INSERT INTO account (id, name, classification, type, detail_type, postable, active)
SELECT '019f4b10-1002-7c02-8d02-0a1b2c3d4e02', 'Иш ҳақи харажати', 'EXPENSE', 'EXPENSE', 'PAYROLL_EXPENSES', true, true
WHERE EXISTS (SELECT 1 FROM account)
  AND NOT EXISTS (SELECT 1 FROM account WHERE detail_type = 'PAYROLL_EXPENSES');
INSERT INTO account (id, name, classification, type, detail_type, postable, active)
SELECT '019f4b10-1003-7c03-8d03-0a1b2c3d4e03', 'Иш ҳақи солиқ харажати', 'EXPENSE', 'EXPENSE', 'PAYROLL_EXPENSES', true, true
WHERE EXISTS (SELECT 1 FROM account)
  AND NOT EXISTS (SELECT 1 FROM account WHERE name = 'Иш ҳақи солиқ харажати');
INSERT INTO account (id, name, classification, type, detail_type, postable, active)
SELECT '019f4b10-1004-7c04-8d04-0a1b2c3d4e04', 'Иш ҳақи солиқлари мажбурияти', 'LIABILITY', 'OTHER_CURRENT_LIABILITY', 'PAYROLL_TAX_PAYABLE', true, true
WHERE EXISTS (SELECT 1 FROM account)
  AND NOT EXISTS (SELECT 1 FROM account WHERE detail_type = 'PAYROLL_TAX_PAYABLE');
--rollback DELETE FROM account WHERE id IN ('019f4b10-1001-7c01-8d01-0a1b2c3d4e01', '019f4b10-1002-7c02-8d02-0a1b2c3d4e02', '019f4b10-1003-7c03-8d03-0a1b2c3d4e03', '019f4b10-1004-7c04-8d04-0a1b2c3d4e04');
