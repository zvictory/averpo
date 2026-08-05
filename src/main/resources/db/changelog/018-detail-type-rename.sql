--liquibase formatted sql

--changeset averpo:018-01-detail-type-rename
-- Detail type'лар QBO расмий AccountSubTypeEnum номларига мослаштирилди
-- (docs/qbo-reference/detail-type-rename-plan.md, 4.5-босқич). Liquibase
-- boot'да enum юкланишидан аввал ишлайди - Account ўқилганда эски string
-- учрамайди. type/classification устунлари ўзгармайди: барча мосликлар
-- ўз тури ичида (текширилган).
UPDATE account SET detail_type = 'SUPPLIES_MATERIALS_COGS' WHERE detail_type = 'SUPPLIES_AND_MATERIALS_COGS';
UPDATE account SET detail_type = 'SUPPLIES_MATERIALS' WHERE detail_type = 'SUPPLIES';
UPDATE account SET detail_type = 'OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES' WHERE detail_type = 'OFFICE_EXPENSES';
UPDATE account SET detail_type = 'PAID_IN_CAPITAL_OR_SURPLUS' WHERE detail_type = 'PAID_IN_CAPITAL';
UPDATE account SET detail_type = 'TRUST_ACCOUNTS' WHERE detail_type = 'TRUST_ACCOUNT';
UPDATE account SET detail_type = 'PAYROLL_CLEARING' WHERE detail_type = 'PAYROLL_LIABILITIES';
-- UNEARNED_REVENUE умумий турга қўшилади - бу кўчиш ОРҚАГА ҚАЙТМАЙДИ
-- (мавжуд OTHER_CURRENT_LIABILITIES қаторларидан ажратиб бўлмайди)
UPDATE account SET detail_type = 'OTHER_CURRENT_LIABILITIES' WHERE detail_type = 'UNEARNED_REVENUE';
--rollback UPDATE account SET detail_type='SUPPLIES_AND_MATERIALS_COGS' WHERE detail_type='SUPPLIES_MATERIALS_COGS'; UPDATE account SET detail_type='SUPPLIES' WHERE detail_type='SUPPLIES_MATERIALS'; UPDATE account SET detail_type='OFFICE_EXPENSES' WHERE detail_type='OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES'; UPDATE account SET detail_type='PAID_IN_CAPITAL' WHERE detail_type='PAID_IN_CAPITAL_OR_SURPLUS'; UPDATE account SET detail_type='TRUST_ACCOUNT' WHERE detail_type='TRUST_ACCOUNTS'; UPDATE account SET detail_type='PAYROLL_LIABILITIES' WHERE detail_type='PAYROLL_CLEARING';
