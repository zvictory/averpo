--liquibase formatted sql

--changeset averpo:036-01-payment-method
-- Тўлов усуллари каталоги (DEC-033, QBO PaymentMethod 9107): фақат
-- name + active - QBO'даги Type (CREDIT_CARD/NON_CREDIT_CARD) атайлаб
-- ОЛИНМАЙДИ (credit card кўлами рад этилган, QBO-001). Усул
-- ЎЧИРИЛМАЙДИ - active=false (каталог қолипи, тарихий ҳужжат изи
-- сақланади).
CREATE TABLE payment_method (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    name       VARCHAR(30) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_payment_method_name UNIQUE (name)
);
--rollback DROP TABLE payment_method;

--changeset averpo:036-02-payment-method-seed
-- Ўзбекистон MVP усуллари. Id'лар олдиндан генерацияланган UUIDv7
-- (032-02 tax seed қолипи).
INSERT INTO payment_method (id, name, active) VALUES
    ('019f8c30-0001-7c11-9e01-0000000000c1', 'Нақд', true),
    ('019f8c30-0002-7c22-9e02-0000000000c2', 'Банк ўтказмаси', true),
    ('019f8c30-0003-7c33-9e03-0000000000c3', 'Пластик карта', true);
--rollback DELETE FROM payment_method WHERE id IN ('019f8c30-0001-7c11-9e01-0000000000c1','019f8c30-0002-7c22-9e02-0000000000c2','019f8c30-0003-7c33-9e03-0000000000c3');

--changeset averpo:036-03-bank-transaction-payment-fields
-- Чиқим экрани майдонлари (QBO Purchase: PaymentMethodRef 5300,
-- DocNumber/Ref no 4564): иккиси ҳам ихтиёрий, deposit ҳам қабул
-- қилади (формасида кейин кўрсатилади). FK - каталог ёзуви ўчмагани
-- (active=false қолипи) учун из «осилиб» қолмайди.
ALTER TABLE bank_transaction ADD COLUMN payment_method_id UUID REFERENCES payment_method(id);
ALTER TABLE bank_transaction ADD COLUMN ref_no VARCHAR(30);
--rollback ALTER TABLE bank_transaction DROP COLUMN payment_method_id; ALTER TABLE bank_transaction DROP COLUMN ref_no;

--changeset averpo:036-04-audit-event-entry-index
-- PERF-025: audit_event.entry_id FK устуни индекссиз - DRAFT JE
-- ўчирилганда referential integrity текшируви audit жадвалини тўлиқ
-- scan қиларди (жадвал append-only, фақат ўсади). Partial - entry_id
-- аксарият ҳолда NULL (auth/user ҳодисалари).
CREATE INDEX ix_audit_event_entry ON audit_event (entry_id) WHERE entry_id IS NOT NULL;
--rollback DROP INDEX ix_audit_event_entry;
