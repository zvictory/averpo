--liquibase formatted sql

--changeset averpo:020-01-landed-cost-allocation
-- Landed cost тақсимот ҳужжати (docs/modules/purchases.md «Landed
-- cost»). Клирингдан ЭРКИН сумма (bill'га боғланмаган - 2026-07-06
-- қарори), DRAFT йўқ: яратилди = POSTED, тузатиш reverse орқали.
-- Суммалар home валютада (омбор қийматлари home'да юритилади).
CREATE TABLE landed_cost_allocation (
    id                UUID PRIMARY KEY,
    version           INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    allocation_number VARCHAR(20) NOT NULL,
    allocation_date   DATE NOT NULL,
    total_amount      NUMERIC(19,4) NOT NULL CHECK (total_amount > 0),
    status            VARCHAR(10) NOT NULL,
    memo              VARCHAR(500),
    CONSTRAINT uq_lca_number UNIQUE (allocation_number)
);
CREATE INDEX idx_lca_date ON landed_cost_allocation(allocation_date);
--rollback DROP TABLE landed_cost_allocation;

--changeset averpo:020-02-landed-cost-allocation-line
-- Тақсимот қатори: қайси receipt'га (BILL манбали кирим ҳаракати)
-- қанча тушгани. inventory/cogs бўлиниши ва тақсимот пайтидаги қолдиқ
-- (remaining_qty_at_alloc) reverse'нинг аниқ гарови учун сақланади.
CREATE TABLE landed_cost_allocation_line (
    id                    UUID PRIMARY KEY,
    version               INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    allocation_id         UUID NOT NULL REFERENCES landed_cost_allocation(id),
    movement_id           UUID NOT NULL REFERENCES stock_movement(id),
    amount                NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    inventory_share       NUMERIC(19,4) NOT NULL CHECK (inventory_share >= 0),
    cogs_share            NUMERIC(19,4) NOT NULL CHECK (cogs_share >= 0),
    remaining_qty_at_alloc NUMERIC(19,4) NOT NULL CHECK (remaining_qty_at_alloc >= 0),
    CONSTRAINT uq_lca_line_movement UNIQUE (allocation_id, movement_id),
    CONSTRAINT ck_lca_line_split CHECK (inventory_share + cogs_share = amount)
);
CREATE INDEX idx_lca_line_alloc ON landed_cost_allocation_line(allocation_id);
CREATE INDEX idx_lca_line_movement ON landed_cost_allocation_line(movement_id);
--rollback DROP TABLE landed_cost_allocation_line;

--changeset averpo:020-03-landed-cost-sequence
-- LC-2026-NNNNN рақамлаш қатори (DocumentType.LANDED_COST). Id -
-- олдиндан генерацияланган UUIDv7 (014-02 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f374e-1c40-7bd2-9f3e-6a1d84c20a11', 'LANDED_COST', 'LC', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type = 'LANDED_COST';
