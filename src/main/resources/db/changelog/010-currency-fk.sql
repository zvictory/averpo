--liquibase formatted sql

-- Currency энди ҳамма entity'да ManyToOne (оддий String эмас) —
-- мавжуд маълумот code бўйича каталогга боғланади.

--changeset averpo:018-account-currency-fk
ALTER TABLE account ADD COLUMN currency_id UUID REFERENCES currency(id);
UPDATE account a SET currency_id = c.id FROM currency c WHERE a.currency = c.code;
ALTER TABLE account DROP COLUMN currency;
--rollback ALTER TABLE account ADD COLUMN currency VARCHAR(3); UPDATE account a SET currency = c.code FROM currency c WHERE a.currency_id = c.id; ALTER TABLE account DROP COLUMN currency_id;

--changeset averpo:019-company-settings-currency-fk
ALTER TABLE company_settings ADD COLUMN home_currency_id UUID REFERENCES currency(id);
UPDATE company_settings s SET home_currency_id = c.id FROM currency c WHERE s.home_currency = c.code;
ALTER TABLE company_settings ALTER COLUMN home_currency_id SET NOT NULL;
ALTER TABLE company_settings DROP COLUMN home_currency;
--rollback ALTER TABLE company_settings ADD COLUMN home_currency VARCHAR(3); UPDATE company_settings s SET home_currency = c.code FROM currency c WHERE s.home_currency_id = c.id; ALTER TABLE company_settings DROP COLUMN home_currency_id;

--changeset averpo:020-contact-currency-fk
ALTER TABLE contact ADD COLUMN currency_id UUID REFERENCES currency(id);
UPDATE contact ct SET currency_id = c.id FROM currency c WHERE ct.currency = c.code;
ALTER TABLE contact DROP COLUMN currency;
--rollback ALTER TABLE contact ADD COLUMN currency VARCHAR(3); UPDATE contact ct SET currency = c.code FROM currency c WHERE ct.currency_id = c.id; ALTER TABLE contact DROP COLUMN currency_id;

--changeset averpo:021-exchange-rate-currency-fk
ALTER TABLE exchange_rate ADD COLUMN currency_id UUID REFERENCES currency(id);
UPDATE exchange_rate er SET currency_id = c.id FROM currency c WHERE er.currency = c.code;
ALTER TABLE exchange_rate ALTER COLUMN currency_id SET NOT NULL;
ALTER TABLE exchange_rate DROP CONSTRAINT uq_exchange_rate;
ALTER TABLE exchange_rate ADD CONSTRAINT uq_exchange_rate UNIQUE (currency_id, rate_date);
ALTER TABLE exchange_rate DROP COLUMN currency;
--rollback ALTER TABLE exchange_rate ADD COLUMN currency VARCHAR(3); UPDATE exchange_rate er SET currency = c.code FROM currency c WHERE er.currency_id = c.id; ALTER TABLE exchange_rate DROP CONSTRAINT uq_exchange_rate; ALTER TABLE exchange_rate ADD CONSTRAINT uq_exchange_rate UNIQUE (currency, rate_date); ALTER TABLE exchange_rate DROP COLUMN currency_id;
