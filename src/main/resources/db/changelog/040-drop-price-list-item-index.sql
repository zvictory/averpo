--liquibase formatted sql

--changeset averpo:040-drop-price-list-item-index
-- PERF-018: idx_price_list_item_list ортиқча - uq_price_list_item
-- UNIQUE (price_list_id, item_id, min_quantity) индексининг биринчи
-- устуни price_list_id бўлгани учун price_list_id бўйича қидирувларни
-- ўша индекс ўзи қоплайди; иккита индексни параллел юритиш ёзувни
-- секинлаштиради холос.
DROP INDEX idx_price_list_item_list;
--rollback CREATE INDEX idx_price_list_item_list ON price_list_item(price_list_id);
