--liquibase formatted sql

--changeset averpo:014-01-document-sequence
-- Умумий ҳужжат рақамлаш (docs/modules/document-sequence.md): тур
-- бўйича созланадиган prefix/padding, рақам жадвал қаторида туради -
-- race'га қарши ҳимоя қатор қулфи (SELECT ... FOR UPDATE) билан.
CREATE TABLE document_sequence (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    document_type VARCHAR(30) NOT NULL,
    prefix        VARCHAR(10) NOT NULL,
    include_year  BOOLEAN NOT NULL DEFAULT true,
    padding       INT NOT NULL DEFAULT 5 CHECK (padding BETWEEN 1 AND 12),
    next_number   BIGINT NOT NULL DEFAULT 1 CHECK (next_number > 0),
    CONSTRAINT uq_document_sequence_type UNIQUE (document_type)
);
--rollback DROP TABLE document_sequence;

--changeset averpo:014-02-document-sequence-seed
-- Id'лар олдиндан генерацияланган UUIDv7. JE қатори next_number'ни
-- мавжуд journal_entry_number_seq ҳолатидан олади - эски JE рақамлари
-- билан тўқнашув бўлмайди (is_called=false бўлса sequence ҳали
-- ишлатилмаган, кейинги қиймат last_value'нинг ўзи). JE padding 6 -
-- мавжуд JE-2026-000001 форматига мос; қолганлари 5 (INV-2026-00001).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
SELECT '019f342b-af89-7cfe-b9ba-f295512ae174', 'JOURNAL_ENTRY', 'JE', true, 6,
       CASE WHEN is_called THEN last_value + 1 ELSE last_value END
FROM journal_entry_number_seq;
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number) VALUES
    ('019f342b-af8a-7e13-8318-0a2de22a67cc', 'INVOICE', 'INV',  true, 5, 1),
    ('019f342b-af8b-75e6-a306-2e10ff069807', 'BILL',    'BILL', true, 5, 1),
    ('019f342b-af8c-746b-a73d-ef6707afbcc7', 'PAYMENT', 'PAY',  true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type IN ('JOURNAL_ENTRY','INVOICE','BILL','PAYMENT');

--changeset averpo:014-03-drop-je-number-seq
-- JE рақамлаш энди document_sequence'дан. Эски DB sequence ўчирилади -
-- иккита манба параллел яшаса рақамлар ажралиб кетиши хавфи бор эди.
DROP SEQUENCE journal_entry_number_seq;
--rollback CREATE SEQUENCE journal_entry_number_seq;
--rollback SELECT setval('journal_entry_number_seq', COALESCE((SELECT max((regexp_match(entry_number, '(\d+)$'))[1]::bigint) FROM journal_entry), 0) + 1, false);
