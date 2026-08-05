--liquibase formatted sql

--changeset averpo:024-01-fiscal-year-start-month
-- QBO «First month of fiscal year» аналоги (docs/modules/reports.md):
-- Balance Sheet'даги Тақсимланмаган фойда / Соф фойда бўлинишини
-- белгилайди. Фақат ҳисобот кўринишига таъсир қилади - сақланган
-- маълумотга тегмайди, шунинг учун қулфланмайди. Default 1 - январь.
ALTER TABLE company_settings
    ADD COLUMN fiscal_year_start_month INTEGER NOT NULL DEFAULT 1;

ALTER TABLE company_settings
    ADD CONSTRAINT ck_company_settings_fy_month
        CHECK (fiscal_year_start_month BETWEEN 1 AND 12);
--rollback ALTER TABLE company_settings DROP COLUMN fiscal_year_start_month;
