--liquibase formatted sql

--changeset averpo:008-rate-scale-12
-- Курс аниқлиги 8 → 12 хона: home currency кучли валюта бўлганда
-- тескари курс (1 UZS = 0.000081967213 USD) 8 хонада фақат 4 та
-- маъноли рақам сақлар эди — бухгалтерия учун етарли эмас.
ALTER TABLE exchange_rate ALTER COLUMN rate TYPE NUMERIC(24,12);
ALTER TABLE journal_entry_line ALTER COLUMN debit_exchange_rate TYPE NUMERIC(24,12);
ALTER TABLE journal_entry_line ALTER COLUMN credit_exchange_rate TYPE NUMERIC(24,12);
--rollback ALTER TABLE exchange_rate ALTER COLUMN rate TYPE NUMERIC(19,8); ALTER TABLE journal_entry_line ALTER COLUMN debit_exchange_rate TYPE NUMERIC(19,8); ALTER TABLE journal_entry_line ALTER COLUMN credit_exchange_rate TYPE NUMERIC(19,8);
