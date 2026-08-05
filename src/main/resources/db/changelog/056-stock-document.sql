--liquibase formatted sql

--changeset averpo:056-01-stock-adjustment
-- Ҳужжатли инвентаризация акти (Arbitr-093, docs/modules/inventory.md):
-- кўп сатрли, БИТТА омбор, дарҳол POSTED (DRAFT йўқ - sales_receipt
-- қолипи), актга БИТТА JE. Сатр StockMovement (ADJUST_IN/OUT,
-- reference_type=STOCK_ADJUSTMENT, reference_id=акт id) билан боғланади.
-- total_cost - актнинг нетто GL таъсири (кўпайиш − камайиш, home).
CREATE TABLE stock_adjustment (
    id             UUID PRIMARY KEY,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    adj_number     VARCHAR(20) NOT NULL,
    warehouse_id   UUID NOT NULL REFERENCES warehouse(id),
    adj_date       DATE NOT NULL,
    total_cost     NUMERIC(19,4) NOT NULL DEFAULT 0,
    status         VARCHAR(10) NOT NULL,
    posted_at      TIMESTAMPTZ,
    memo           VARCHAR(500),
    CONSTRAINT uq_stock_adjustment_number UNIQUE (adj_number)
);
--rollback DROP TABLE stock_adjustment;

--changeset averpo:056-02-stock-adjustment-line
-- Сатр: item, ЯНГИ qty (киритилади) → delta авто (new − жорий),
-- unit_cost ихтиёрий (кўпайишда, BR-INV-007 сатрга), delta_qty ва
-- line_cost (сатрнинг GL таъсири, home) snapshot. UNIQUE(акт, item)
-- BR-INV-012, UNIQUE(акт, line_no) тартиб.
CREATE TABLE stock_adjustment_line (
    id                   UUID PRIMARY KEY,
    version              INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID,
    stock_adjustment_id  UUID NOT NULL REFERENCES stock_adjustment(id),
    line_no              INT NOT NULL,
    item_id              UUID NOT NULL REFERENCES item(id),
    new_qty              NUMERIC(19,4) NOT NULL,
    delta_qty            NUMERIC(19,4) NOT NULL,
    unit_cost            NUMERIC(24,12),
    line_cost            NUMERIC(19,4) NOT NULL DEFAULT 0,
    memo                 VARCHAR(500),
    CONSTRAINT uq_stock_adjustment_line_no UNIQUE (stock_adjustment_id, line_no),
    CONSTRAINT uq_stock_adjustment_line_item UNIQUE (stock_adjustment_id, item_id)
);
CREATE INDEX idx_stock_adjustment_warehouse ON stock_adjustment(warehouse_id);
CREATE INDEX idx_stock_adjustment_date ON stock_adjustment(adj_date);
--rollback DROP TABLE stock_adjustment_line;

--changeset averpo:056-03-stock-transfer
-- Ҳужжатли омборлараро кўчириш акти (Arbitr-093): кўп сатрли, манба/
-- манзил омбор (BR-INV-005 акт даражасида), GL'сиз. Сатр ҳар бири
-- TRANSFER_OUT+IN жуфти (reference акт id). total_cost - кўчган
-- умумий қиймат (аудит учун, home).
CREATE TABLE stock_transfer (
    id                  UUID PRIMARY KEY,
    version             INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    wtr_number          VARCHAR(20) NOT NULL,
    from_warehouse_id   UUID NOT NULL REFERENCES warehouse(id),
    to_warehouse_id     UUID NOT NULL REFERENCES warehouse(id),
    wtr_date            DATE NOT NULL,
    total_cost          NUMERIC(19,4) NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL,
    posted_at           TIMESTAMPTZ,
    memo                VARCHAR(500),
    CONSTRAINT uq_stock_transfer_number UNIQUE (wtr_number)
);
--rollback DROP TABLE stock_transfer;

--changeset averpo:056-04-stock-transfer-line
-- Сатр: item, qty. line_cost - кўчган қиймат snapshot (аудит).
-- UNIQUE(акт, item) BR-INV-012, UNIQUE(акт, line_no) тартиб.
CREATE TABLE stock_transfer_line (
    id                 UUID PRIMARY KEY,
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    stock_transfer_id  UUID NOT NULL REFERENCES stock_transfer(id),
    line_no            INT NOT NULL,
    item_id            UUID NOT NULL REFERENCES item(id),
    quantity           NUMERIC(19,4) NOT NULL,
    line_cost          NUMERIC(19,4) NOT NULL DEFAULT 0,
    memo               VARCHAR(500),
    CONSTRAINT uq_stock_transfer_line_no UNIQUE (stock_transfer_id, line_no),
    CONSTRAINT uq_stock_transfer_line_item UNIQUE (stock_transfer_id, item_id)
);
CREATE INDEX idx_stock_transfer_from ON stock_transfer(from_warehouse_id);
CREATE INDEX idx_stock_transfer_to ON stock_transfer(to_warehouse_id);
CREATE INDEX idx_stock_transfer_date ON stock_transfer(wtr_date);
--rollback DROP TABLE stock_transfer_line;

--changeset averpo:056-05-stock-document-sequence
-- ADJ-2026-NNNNN ва WTR-2026-NNNNN рақамлаш қаторлари (DocumentType
-- STOCK_ADJUSTMENT/STOCK_TRANSFER). Префикслар grep билан эркин
-- текширилган. Id олдиндан генерацияланган UUIDv7 (014-02 услуби).
INSERT INTO document_sequence (id, document_type, prefix, include_year, padding, next_number)
VALUES ('019f5c93-0001-7a01-8c01-0a1b2c3d5601', 'STOCK_ADJUSTMENT', 'ADJ', true, 5, 1),
       ('019f5c93-0002-7a02-8c02-0a1b2c3d5602', 'STOCK_TRANSFER',   'WTR', true, 5, 1);
--rollback DELETE FROM document_sequence WHERE document_type IN ('STOCK_ADJUSTMENT', 'STOCK_TRANSFER');
