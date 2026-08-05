--liquibase formatted sql

--changeset averpo:035-01-exchange-rate-lookup-index
-- Arbitr-028 (Beruniy-019 + Beruniy-022): 033-exchange-rate-history
-- uq_exchange_rate constraint'ини DROP қилганда унинг implicit B-tree
-- индекси ҳам ўчган эди - энг қизғин ўқиш йўли (rateFor/latest/history/
-- дубль-скип: currency_id филтри + rate_date, id DESC тартиби) жадвал
-- ўсиши билан seq scan бўлиб қоларди. DESC тартиб query'ларнинг
-- ORDER BY rate_date DESC, id DESC кўринишига мос - LIMIT 1 lookup
-- индекс бошидан ўқийди.
CREATE INDEX idx_exchange_rate_lookup
    ON exchange_rate (currency_id, rate_date DESC, id DESC);
--rollback DROP INDEX idx_exchange_rate_lookup;
