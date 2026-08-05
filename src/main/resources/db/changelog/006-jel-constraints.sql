--liquibase formatted sql

--changeset averpo:009-jel-constraints
-- Service валидацияси биринчи ҳимоя, лекин bug/manual SQL/import
-- нотўғри сатр ёзиб қўйиши мумкин — инвариантлар DB даражасида ҳам
-- мустаҳкамланади (posting-rules.md, 2-инвариант).
ALTER TABLE journal_entry_line
    ADD CONSTRAINT uq_jel_entry_line_no UNIQUE (entry_id, line_no);

-- Дебет XOR кредит: фақат биттаси, мусбат, base amount ҳам мусбат
ALTER TABLE journal_entry_line
    ADD CONSTRAINT ck_jel_debit_credit_xor CHECK (
        (debit_amount IS NOT NULL AND debit_amount > 0
             AND debit_base_amount IS NOT NULL AND debit_base_amount > 0
             AND credit_amount IS NULL)
        OR
        (credit_amount IS NOT NULL AND credit_amount > 0
             AND credit_base_amount IS NOT NULL AND credit_base_amount > 0
             AND debit_amount IS NULL)
    );
--rollback ALTER TABLE journal_entry_line DROP CONSTRAINT ck_jel_debit_credit_xor; ALTER TABLE journal_entry_line DROP CONSTRAINT uq_jel_entry_line_no;
