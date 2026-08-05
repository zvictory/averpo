--liquibase formatted sql

--changeset averpo:031-01-price-list
-- Нарх рўйхатлари (docs/modules/price-list.md, old-erp-ideas §10):
-- рўйхат валютали ва даврли, битта default (partial unique).
CREATE TABLE price_list (
    id          UUID PRIMARY KEY,
    version     INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    name        VARCHAR(100) NOT NULL,
    currency_id UUID NOT NULL REFERENCES currency(id),
    valid_from  DATE,
    valid_to    DATE,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    active      BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_price_list_name UNIQUE (name),
    CONSTRAINT ck_price_list_dates CHECK (valid_from IS NULL
        OR valid_to IS NULL OR valid_from <= valid_to)
);
-- BR-PL-003: биттагина default рўйхат (service алмашувни ўзи қилади)
CREATE UNIQUE INDEX ux_price_list_default ON price_list (is_default)
    WHERE is_default;
--rollback DROP TABLE price_list;

--changeset averpo:031-02-price-list-item
-- Поғонали нарх: (рўйхат, item, min_quantity) unique - BR-PL-005.
-- Нарх item BASE бирлигига, рўйхат валютасида.
CREATE TABLE price_list_item (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    price_list_id UUID NOT NULL REFERENCES price_list(id),
    item_id       UUID NOT NULL REFERENCES item(id),
    min_quantity  NUMERIC(19,4) NOT NULL DEFAULT 1 CHECK (min_quantity > 0),
    price         NUMERIC(19,4) NOT NULL CHECK (price >= 0),
    CONSTRAINT uq_price_list_item UNIQUE (price_list_id, item_id, min_quantity)
);
CREATE INDEX idx_price_list_item_list ON price_list_item(price_list_id);
--rollback DROP TABLE price_list_item;

--changeset averpo:031-03-price-list-customer
-- Мижоз бириктируви РЎЙХАТ томонида (QBO Price rules услуби) -
-- contact модулига тегилмайди. Мижозга биттагина рўйхат (BR-PL-006).
CREATE TABLE price_list_customer (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    price_list_id UUID NOT NULL REFERENCES price_list(id),
    customer_id   UUID NOT NULL REFERENCES contact(id),
    CONSTRAINT uq_price_list_customer UNIQUE (customer_id)
);
CREATE INDEX idx_price_list_customer_list ON price_list_customer(price_list_id);
--rollback DROP TABLE price_list_customer;
