--liquibase formatted sql

--changeset averpo:022-01-bank-transaction
-- Банк транзакцияси (docs/modules/banking.md): DEPOSIT (кўп сатрли
-- кирим, QBO Bank Deposit) / EXPENSE (тўғридан-тўғри чиқим) /
-- TRANSFER (ўтказма, конверсия билан). DRAFT йўқ: яратилди = POSTED.
-- Ҳужжат валютаси банк счётидан келади. counterpart_* - transfer'нинг
-- манзил томони (конверсияда суммаси/курси фарқли).
CREATE TABLE bank_transaction (
    id                      UUID PRIMARY KEY,
    version                 INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    txn_number              VARCHAR(20) NOT NULL,
    type                    VARCHAR(10) NOT NULL,
    bank_account_id         UUID NOT NULL REFERENCES account(id),
    counterpart_account_id  UUID REFERENCES account(id),
    txn_date                DATE NOT NULL,
    currency_id             UUID NOT NULL REFERENCES currency(id),
    exchange_rate           NUMERIC(24,12) NOT NULL CHECK (exchange_rate > 0),
    counterpart_amount      NUMERIC(19,4) CHECK (counterpart_amount IS NULL OR counterpart_amount > 0),
    counterpart_rate        NUMERIC(24,12) CHECK (counterpart_rate IS NULL OR counterpart_rate > 0),
    total                   NUMERIC(19,4) NOT NULL CHECK (total > 0),
    total_base              NUMERIC(19,4) NOT NULL CHECK (total_base > 0),
    contact_id              UUID REFERENCES contact(id),
    status                  VARCHAR(10) NOT NULL,
    memo                    VARCHAR(500),
    CONSTRAINT uq_bank_txn_number UNIQUE (txn_number)
);
CREATE INDEX idx_bank_txn_account ON bank_transaction(bank_account_id);
CREATE INDEX idx_bank_txn_date ON bank_transaction(txn_date);
--rollback DROP TABLE bank_transaction;

--changeset averpo:022-02-bank-transaction-line
-- DEPOSIT/EXPENSE сатрлари (TRANSFER'да сатр йўқ): манба/харажат
-- счёти + сумма (ҳужжат валютасида) + ихтиёрий контакт (QBO deposit
-- "received from").
CREATE TABLE bank_transaction_line (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    txn_id     UUID NOT NULL REFERENCES bank_transaction(id),
    line_no    INT NOT NULL,
    account_id UUID NOT NULL REFERENCES account(id),
    amount     NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    contact_id UUID REFERENCES contact(id),
    memo       VARCHAR(500)
);
CREATE INDEX idx_bank_txn_line_txn ON bank_transaction_line(txn_id);
--rollback DROP TABLE bank_transaction_line;

--changeset averpo:022-03-bank-reconciliation
-- QBO Reconcile модели (2026-07-06 қарори): кўчирма сатрлари йўқ -
-- давр + якуний қолдиқ, GL сатрлари белгиланади. Қолдиқлар СЧЁТ
-- ВАЛЮТАСИДА. Opening кейингиларда аввалги COMPLETED'нинг closing'идан.
CREATE TABLE bank_reconciliation (
    id              UUID PRIMARY KEY,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    account_id      UUID NOT NULL REFERENCES account(id),
    statement_date  DATE NOT NULL,
    opening_balance NUMERIC(19,4) NOT NULL,
    closing_balance NUMERIC(19,4) NOT NULL,
    status          VARCHAR(15) NOT NULL,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT uq_bank_recon_account_date UNIQUE (account_id, statement_date)
);
CREATE INDEX idx_bank_recon_account ON bank_reconciliation(account_id);
--rollback DROP TABLE bank_reconciliation;

--changeset averpo:022-04-bank-reconciliation-match
-- Белгиланган GL сатрлари. journal_entry_line_id - dimension паттерни
-- (DB FK, JPA'да UUID - ledger'га entity боғланиш йўқ, қоида №6).
-- ГЛОБАЛ unique: сатр фақат бир марта reconcile қилинади (BR-RCN-006).
-- amount - сатр суммаси СЧЁТ ВАЛЮТАСИДА, ишорали (Dt +, Cr -):
-- фарқ ҳисоби ledger'га қайта мурожаатсиз (POSTED сатр ўзгармас -
-- snapshot хавфсиз).
CREATE TABLE bank_reconciliation_match (
    id                    UUID PRIMARY KEY,
    version               INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    reconciliation_id     UUID NOT NULL REFERENCES bank_reconciliation(id),
    journal_entry_line_id UUID NOT NULL REFERENCES journal_entry_line(id),
    amount                NUMERIC(19,4) NOT NULL,
    CONSTRAINT uq_bank_recon_match_line UNIQUE (journal_entry_line_id)
);
CREATE INDEX idx_bank_recon_match_recon ON bank_reconciliation_match(reconciliation_id);
--rollback DROP TABLE bank_reconciliation_match;

--changeset averpo:022-05-bank-txn-sequence
-- BT-2026-NNNNN рақамлаш қатори (DocumentType.BANK_TXN). Id -
-- олдиндан генерацияланган UUIDv7 (014-02 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f381e-7c55-73d9-8e41-b62f0a97c4d2', 'BANK_TXN', 'BT', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'BANK_TXN';
