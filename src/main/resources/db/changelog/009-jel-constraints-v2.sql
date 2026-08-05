--liquibase formatted sql

--changeset averpo:017-jel-constraints-v2
-- v1 constraint тўлиқ эмас эди: фаол томонда currency/rate талаб
-- қилинмасди, нофаол томонда фақат amount NULL текширилар эди.
-- Тўлиқ инвариант: фаол томоннинг ҲАММА устунлари тўлдирилган,
-- нофаол томонники ҲАММАСИ NULL.
ALTER TABLE journal_entry_line DROP CONSTRAINT ck_jel_debit_credit_xor;
ALTER TABLE journal_entry_line ADD CONSTRAINT ck_jel_debit_credit_xor CHECK (
    (debit_amount IS NOT NULL AND debit_amount > 0
         AND debit_base_amount IS NOT NULL AND debit_base_amount > 0
         AND debit_currency IS NOT NULL AND debit_exchange_rate IS NOT NULL
         AND credit_amount IS NULL AND credit_base_amount IS NULL
         AND credit_currency IS NULL AND credit_exchange_rate IS NULL)
    OR
    (credit_amount IS NOT NULL AND credit_amount > 0
         AND credit_base_amount IS NOT NULL AND credit_base_amount > 0
         AND credit_currency IS NOT NULL AND credit_exchange_rate IS NOT NULL
         AND debit_amount IS NULL AND debit_base_amount IS NULL
         AND debit_currency IS NULL AND debit_exchange_rate IS NULL)
);
--rollback ALTER TABLE journal_entry_line DROP CONSTRAINT ck_jel_debit_credit_xor;
--rollback ALTER TABLE journal_entry_line ADD CONSTRAINT ck_jel_debit_credit_xor CHECK ((debit_amount IS NOT NULL AND debit_amount > 0 AND debit_base_amount IS NOT NULL AND debit_base_amount > 0 AND credit_amount IS NULL) OR (credit_amount IS NOT NULL AND credit_amount > 0 AND credit_base_amount IS NOT NULL AND credit_base_amount > 0 AND debit_amount IS NULL));
