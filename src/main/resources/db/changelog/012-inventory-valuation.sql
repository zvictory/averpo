--liquibase formatted sql

--changeset averpo:023-inventory-valuation
-- Inventory баҳолаш методи (AVCO/FIFO) — компания даражасида,
-- 5-босқичда inventory модули шу қийматга қараб ишлайди
ALTER TABLE company_settings
    ADD COLUMN inventory_valuation VARCHAR(10) NOT NULL DEFAULT 'AVCO';
--rollback ALTER TABLE company_settings DROP COLUMN inventory_valuation;
