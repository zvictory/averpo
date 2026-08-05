--liquibase formatted sql

--changeset averpo:017-01-warehouse
-- Омборлар каталоги (docs/modules/inventory.md). Ўчириш йўқ - фақат
-- active=false, ҳаракатлар тарихи бузилмайди.
CREATE TABLE warehouse (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    name       VARCHAR(255) NOT NULL,
    code       VARCHAR(20),
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_warehouse_name UNIQUE (name)
);
CREATE UNIQUE INDEX ux_warehouse_code ON warehouse(code) WHERE code IS NOT NULL;
--rollback DROP TABLE warehouse;

--changeset averpo:017-02-warehouse-seed
-- Операциялар омборсиз юрмайди - битта default омбор seed қилинади
-- (id олдиндан генерацияланган UUIDv7).
INSERT INTO warehouse (id, name, code) VALUES
    ('019f348b-81c5-7d1f-a7a2-d863f0f5fd26', 'Асосий омбор', 'MAIN');
--rollback DELETE FROM warehouse WHERE id = '019f348b-81c5-7d1f-a7a2-d863f0f5fd26';

--changeset averpo:017-03-stock-movement
-- Ўзгармас ҳаракатлар журнали. item_id - dimension паттерни (DB FK бор,
-- JPA'да оддий UUID - модуллараро entity боғланиш йўқ). Transfer иккита
-- ёзув (TRANSFER_OUT + TRANSFER_IN), counterpart орқали боғланади.
CREATE TABLE stock_movement (
    id                        UUID PRIMARY KEY,
    version                   INT NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    type                      VARCHAR(20) NOT NULL,
    item_id                   UUID NOT NULL REFERENCES item(id),
    warehouse_id              UUID NOT NULL REFERENCES warehouse(id),
    counterpart_warehouse_id  UUID REFERENCES warehouse(id),
    quantity                  NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_cost                 NUMERIC(24,12) NOT NULL CHECK (unit_cost >= 0),
    total_cost                NUMERIC(19,4) NOT NULL CHECK (total_cost >= 0),
    movement_date             DATE NOT NULL,
    reference_type            VARCHAR(30),
    reference_id              UUID,
    memo                      VARCHAR(500)
);
CREATE INDEX idx_stock_movement_item_wh ON stock_movement(item_id, warehouse_id, movement_date);
CREATE INDEX idx_stock_movement_reference ON stock_movement(reference_type, reference_id);
--rollback DROP TABLE stock_movement;

--changeset averpo:017-04-stock-balance
-- Жорий қолдиқ (warehouse, item) кесимида. qty >= 0 - манфий қолдиқ
-- тақиқи DB инварианти сифатида ҳам туради (BR-INV-003).
CREATE TABLE stock_balance (
    id           UUID PRIMARY KEY,
    version      INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    warehouse_id UUID NOT NULL REFERENCES warehouse(id),
    item_id      UUID NOT NULL REFERENCES item(id),
    qty          NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (qty >= 0),
    avg_cost     NUMERIC(24,12) NOT NULL DEFAULT 0 CHECK (avg_cost >= 0),
    CONSTRAINT uq_stock_balance UNIQUE (warehouse_id, item_id)
);
--rollback DROP TABLE stock_balance;

--changeset averpo:017-05-cost-layer
-- FIFO партиялари. Partial index - «кейинги ейилмаган layer» қидируви
-- катта тарихда ҳам тез (old-erp-ideas §6).
CREATE TABLE cost_layer (
    id                 UUID PRIMARY KEY,
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    warehouse_id       UUID NOT NULL REFERENCES warehouse(id),
    item_id            UUID NOT NULL REFERENCES item(id),
    received_date      DATE NOT NULL,
    unit_cost          NUMERIC(24,12) NOT NULL CHECK (unit_cost >= 0),
    original_qty       NUMERIC(19,4) NOT NULL CHECK (original_qty > 0),
    remaining_qty      NUMERIC(19,4) NOT NULL CHECK (remaining_qty >= 0 AND remaining_qty <= original_qty),
    is_exhausted       BOOLEAN NOT NULL DEFAULT false,
    source_movement_id UUID NOT NULL REFERENCES stock_movement(id)
);
CREATE INDEX idx_cost_layer_next ON cost_layer(warehouse_id, item_id, received_date, id)
    WHERE NOT is_exhausted;
--rollback DROP TABLE cost_layer;

--changeset averpo:017-06-cost-layer-consumption
-- Қайси партия қайси чиқимга ейилгани - тўлиқ audit из (FIFO).
CREATE TABLE cost_layer_consumption (
    id          UUID PRIMARY KEY,
    version     INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    layer_id    UUID NOT NULL REFERENCES cost_layer(id),
    movement_id UUID NOT NULL REFERENCES stock_movement(id),
    quantity    NUMERIC(19,4) NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_clc_layer ON cost_layer_consumption(layer_id);
CREATE INDEX idx_clc_movement ON cost_layer_consumption(movement_id);
--rollback DROP TABLE cost_layer_consumption;
