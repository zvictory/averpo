--liquibase formatted sql

--changeset averpo:013-01-je-reversal-of
-- Сторно entry аслига ҳавола. Сторно атайлаб бир хил (source_module,
-- source_document_id) билан ёзилади - қуйидаги partial unique index'дан
-- уни чиқариб туриш учун DB даражасида белги керак.
ALTER TABLE journal_entry ADD COLUMN reversal_of_id UUID REFERENCES journal_entry(id);
--rollback ALTER TABLE journal_entry DROP COLUMN reversal_of_id;

--changeset averpo:013-02-je-source-partial-unique
-- BR-LED-012 энди DB даражасида ҳам кафолатланади: parallel иккита
-- createDraft келса, service текшируви иккаласини ўтказиб юбориши
-- мумкин - иккинчи INSERT шу index'га йиқилади. REVERSED'дан кейин
-- қайта post мумкин (асл entry status орқали index'дан чиқади),
-- сторно эса reversal_of_id билан четда қолади.
CREATE UNIQUE INDEX ux_je_source_active ON journal_entry (source_module, source_document_id)
    WHERE source_document_id IS NOT NULL
      AND status IN ('DRAFT', 'POSTED')
      AND reversal_of_id IS NULL;
--rollback DROP INDEX ux_je_source_active;

--changeset averpo:013-03-jel-rate-positive
-- Курс мусбатлиги service'да текширилади, лекин DB invariant тўлиқ
-- бўлиши шарт: 0 ёки манфий курс baseAmount формуласини бузади.
ALTER TABLE journal_entry_line ADD CONSTRAINT ck_jel_debit_rate_positive
    CHECK (debit_exchange_rate IS NULL OR debit_exchange_rate > 0);
ALTER TABLE journal_entry_line ADD CONSTRAINT ck_jel_credit_rate_positive
    CHECK (credit_exchange_rate IS NULL OR credit_exchange_rate > 0);
--rollback ALTER TABLE journal_entry_line DROP CONSTRAINT ck_jel_debit_rate_positive; ALTER TABLE journal_entry_line DROP CONSTRAINT ck_jel_credit_rate_positive;
