--liquibase formatted sql

--changeset averpo:044-01-payroll-run
-- Ойлик иш ҳақи ҳисоблаши (docs/modules/payroll.md «PayrollRun»):
-- DRAFT таҳрирланади, POSTED ўзгармас - фақат reverse (invoice
-- қолипи). Ҳамма суммалар home валютада (BR-PYR-001) - валюта/курс
-- устунлари атайлаб йўқ. entry_id - post'да ёзилган JE ҳаваласи
-- (кўриш экрани linki, audit entryId нақши).
CREATE TABLE payroll_run (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    run_number VARCHAR(20) NOT NULL,
    period     VARCHAR(7) NOT NULL,
    run_date   DATE NOT NULL,
    status     VARCHAR(10) NOT NULL,
    entry_id   UUID,
    memo       VARCHAR(500),
    CONSTRAINT uq_payroll_run_number UNIQUE (run_number)
);
--rollback DROP TABLE payroll_run;

--changeset averpo:044-02-payroll-run-period-posted-unique
-- BR-PYR-002: битта ойга биттагина POSTED run - ux_je_source_active
-- қолипидаги partial unique (service текшируви + DB кафолати).
CREATE UNIQUE INDEX ux_payroll_run_period_posted
    ON payroll_run (period) WHERE status = 'POSTED';
--rollback DROP INDEX ux_payroll_run_period_posted;

--changeset averpo:044-03-payroll-run-line
-- Ҳисоблаш сатри: ходим + gross ва ҳисобланган СУММА snapshot'лари
-- (income_tax/pension/social_tax/net) - кейин CompanySettings ставкаси
-- ўзгарса тарихий ҳужжат ўзгармайди (payroll.md «Қатъий қарорлар»).
-- class_id - Йўналиш, фақат харажат легига кўчади (class-tracking.md).
-- UNIQUE (run_id, employee_id) - бир run'да ходим бир марта
-- (BR-PYR-003); (run_id, line_no) - Beruniy-010 қолипи.
CREATE TABLE payroll_run_line (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    payroll_run_id UUID NOT NULL REFERENCES payroll_run(id),
    line_no        INT NOT NULL,
    employee_id    UUID NOT NULL REFERENCES contact(id),
    gross          NUMERIC(19,4) NOT NULL,
    income_tax     NUMERIC(19,4) NOT NULL,
    pension        NUMERIC(19,4) NOT NULL,
    social_tax     NUMERIC(19,4) NOT NULL,
    net            NUMERIC(19,4) NOT NULL,
    class_id       UUID REFERENCES txn_class(id),
    memo           VARCHAR(500),
    CONSTRAINT uq_payroll_run_line_employee UNIQUE (payroll_run_id, employee_id),
    CONSTRAINT uq_payroll_run_line_no UNIQUE (payroll_run_id, line_no)
);
--rollback DROP TABLE payroll_run_line;

--changeset averpo:044-04-payroll-run-sequence
-- PAYR-2026-NNNNN рақамлаш қатори (DocumentType.PAYROLL_RUN). Spec'даги
-- PAY префикси 014-changeset'дан бери PAYMENT'га банд - икки ҳужжат
-- тури бир хил кўринишдаги рақам бермаслиги учун PAYR (23в тўлови
-- PAYP бўлади). Id - олдиндан генерацияланган UUIDv7 (014-02 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f3f93-6e90-7072-982b-f3efc45a6303', 'PAYROLL_RUN', 'PAYR', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'PAYROLL_RUN';
