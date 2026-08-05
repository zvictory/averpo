--liquibase formatted sql

--changeset averpo:049-01-stock-movement-date-index
-- Инвентар валюацияси санаси бўйича сканни тезлатиш (DEC-033, PERF-033).
-- InventoryValuationService.build(asOf) ягона `WHERE m.movement_date <= :asOf`
-- фильтри билан бутун stock_movement'ни (item, омбор) кесимида йиғади -
-- item_id/warehouse_id олдиндан берилмайди (омбор фильтри Java томонда).
-- Мавжуд idx_stock_movement_item_wh (item_id, warehouse_id, movement_date)
-- date-first ЭМАС, шунга бу фильтрга ярамайди (prefix мос эмас) - PostgreSQL
-- катта history seq-scan қиларди. Date-first индекс asOf гача range scan
-- беради (dashboard inventory card ва валюация ҳисоботи учун).
CREATE INDEX idx_stock_movement_date ON stock_movement(movement_date);
--rollback DROP INDEX idx_stock_movement_date;
