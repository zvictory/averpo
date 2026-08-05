--liquibase formatted sql

--changeset averpo:013-unit
-- Ўлчов бирлиги - QBO'да йўқ, multi-warehouse кенгайтмамиз талаби
CREATE TABLE unit (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    name       VARCHAR(50) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_unit_name UNIQUE (name)
);
--rollback DROP TABLE unit;

--changeset averpo:014-unit-seed
INSERT INTO unit (id, name, active) VALUES
    ('019f337a-8414-7469-80d4-c091bbb42b2a', 'дона', true),
    ('019f337a-8415-7d1a-a7c3-877ad2ae3fd6', 'кг', true),
    ('019f337a-8415-7659-ac04-cb9434ea40f1', 'литр', true),
    ('019f337a-8415-7a64-a93a-f849358814d3', 'метр', true),
    ('019f337a-8415-7c02-9512-2322dd148b18', 'соат', true),
    ('019f337a-8415-77ad-bb54-002766e1b043', 'хизмат', true);
--rollback DELETE FROM unit WHERE name IN ('дона','кг','литр','метр','соат','хизмат');

--changeset averpo:015-item-category
CREATE TABLE item_category (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    name       VARCHAR(255) NOT NULL,
    parent_id  UUID REFERENCES item_category(id),
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_item_category_name UNIQUE (name)
);
CREATE INDEX idx_item_category_parent ON item_category(parent_id);
--rollback DROP TABLE item_category;

--changeset averpo:016-item
CREATE TABLE item (
    id                          UUID PRIMARY KEY,
    version                     INT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    type                        VARCHAR(15) NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    sku                         VARCHAR(50),
    category_id                 UUID REFERENCES item_category(id),
    unit_id                     UUID REFERENCES unit(id),
    sales_price                 NUMERIC(19,4) CHECK (sales_price >= 0),
    sales_description           TEXT,
    income_account_id           UUID NOT NULL,
    purchase_cost               NUMERIC(19,4) CHECK (purchase_cost >= 0),
    purchase_description        TEXT,
    expense_account_id          UUID NOT NULL,
    inventory_asset_account_id  UUID,
    reorder_point               NUMERIC(19,4),
    active                      BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_item_name UNIQUE (name)
);
-- SKU ихтиёрий, киритилса unique (account.code паттерни)
CREATE UNIQUE INDEX uq_item_sku ON item(sku) WHERE sku IS NOT NULL;
CREATE INDEX idx_item_type ON item(type);
CREATE INDEX idx_item_category ON item(category_id);
--rollback DROP TABLE item;
