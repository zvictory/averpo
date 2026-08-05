--liquibase formatted sql

--changeset averpo:033-01-exchange-rate-history
-- Курс тарихи (docs/modules/transfer.md Т3, Arbitr-022): бир (валюта,
-- сана)га КЎП ёзув - ЦБ импорти ва қўлда/ўтказма ўзгартиришлар устига
-- ёзилмайди, ҳар бири сақланади. uq_exchange_rate олиб ташланади,
-- source (CBU/MANUAL) қўшилади. Амалдаги курс = энг охирги ёзув
-- (rate_date <= сана, кейин UUIDv7 id). Мавжуд ёзувлар CBU деб белгиланади.
ALTER TABLE exchange_rate DROP CONSTRAINT uq_exchange_rate;
ALTER TABLE exchange_rate ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'CBU';
--rollback DELETE FROM exchange_rate e WHERE EXISTS (SELECT 1 FROM exchange_rate n WHERE n.currency_id = e.currency_id AND n.rate_date = e.rate_date AND n.id > e.id);
--rollback ALTER TABLE exchange_rate DROP COLUMN source; ALTER TABLE exchange_rate ADD CONSTRAINT uq_exchange_rate UNIQUE (currency_id, rate_date);
