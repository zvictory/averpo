--liquibase formatted sql

--changeset averpo:034-01-audit-event
-- Аудит журнали (docs/modules/audit-log.md, QBO Audit Log паритети):
-- append-only ҳодисалар - update/delete на UI'да, на service'да.
-- username алоҳида устун (created_by UUID'ига қарамай): LOGIN_FAILURE'да
-- authenticated principal йўқ, уринилган username'га жой керак; қолган
-- ҳолларда ҳам экран JOIN'сиз ўқийди. Ҳодиса вақти = created_at (UTC),
-- бир транзакция ичидаги тартиб UUIDv7 id билан (курс тарихи нақши).
CREATE TABLE audit_event (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    username   VARCHAR(50) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    entry_id   UUID REFERENCES journal_entry(id),
    doc_number VARCHAR(30),
    details    VARCHAR(500),
    ip_address VARCHAR(45)
);
-- Рўйхат экрани доим янгидан эскига ўқийди
CREATE INDEX ix_audit_event_created_at ON audit_event (created_at DESC);
-- Филтрлар: тур ва фойдаланувчи бўйича
CREATE INDEX ix_audit_event_type ON audit_event (event_type);
CREATE INDEX ix_audit_event_username ON audit_event (username);
--rollback DROP TABLE audit_event;
