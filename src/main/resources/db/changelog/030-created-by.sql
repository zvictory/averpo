--liquibase formatted sql

--changeset averpo:030-01-created-by
-- createdBy аудит майдони (SEC-004 §7, user-management.md): BaseEntity
-- @CreatedBy бўлгани учун БАРЧА жадвалларга бир хил устун керак
-- (ddl-auto=validate). FK атайлаб ЙЎҚ - dimension паттерни (модул
-- мустақиллиги); app_user ўчирилмагани (фақат active=false) учун из
-- «осилиб» қолмайди. Эски ёзувлар NULL - «миграциядан олдинги ёзув»,
-- сохта атрибуция қилинмайди; auth контекстисиз ёзувлар (scheduler,
-- bootstrap) ҳам NULL қолади.
ALTER TABLE account ADD COLUMN created_by UUID;
ALTER TABLE app_user ADD COLUMN created_by UUID;
ALTER TABLE bank_reconciliation ADD COLUMN created_by UUID;
ALTER TABLE bank_reconciliation_match ADD COLUMN created_by UUID;
ALTER TABLE bank_transaction ADD COLUMN created_by UUID;
ALTER TABLE bank_transaction_line ADD COLUMN created_by UUID;
ALTER TABLE bill ADD COLUMN created_by UUID;
ALTER TABLE bill_line ADD COLUMN created_by UUID;
ALTER TABLE bill_payment ADD COLUMN created_by UUID;
ALTER TABLE bill_payment_allocation ADD COLUMN created_by UUID;
ALTER TABLE company_settings ADD COLUMN created_by UUID;
ALTER TABLE contact ADD COLUMN created_by UUID;
ALTER TABLE contact_address ADD COLUMN created_by UUID;
ALTER TABLE contact_bank_account ADD COLUMN created_by UUID;
ALTER TABLE contact_person ADD COLUMN created_by UUID;
ALTER TABLE cost_layer ADD COLUMN created_by UUID;
ALTER TABLE cost_layer_consumption ADD COLUMN created_by UUID;
ALTER TABLE currency ADD COLUMN created_by UUID;
ALTER TABLE document_sequence ADD COLUMN created_by UUID;
ALTER TABLE exchange_rate ADD COLUMN created_by UUID;
ALTER TABLE invoice ADD COLUMN created_by UUID;
ALTER TABLE invoice_line ADD COLUMN created_by UUID;
ALTER TABLE invoice_payment ADD COLUMN created_by UUID;
ALTER TABLE invoice_payment_allocation ADD COLUMN created_by UUID;
ALTER TABLE item ADD COLUMN created_by UUID;
ALTER TABLE item_category ADD COLUMN created_by UUID;
ALTER TABLE journal_entry ADD COLUMN created_by UUID;
ALTER TABLE journal_entry_line ADD COLUMN created_by UUID;
ALTER TABLE landed_cost_allocation ADD COLUMN created_by UUID;
ALTER TABLE landed_cost_allocation_line ADD COLUMN created_by UUID;
ALTER TABLE payment_term ADD COLUMN created_by UUID;
ALTER TABLE stock_balance ADD COLUMN created_by UUID;
ALTER TABLE stock_movement ADD COLUMN created_by UUID;
ALTER TABLE unit ADD COLUMN created_by UUID;
ALTER TABLE unit_group ADD COLUMN created_by UUID;
ALTER TABLE warehouse ADD COLUMN created_by UUID;
--rollback ALTER TABLE account DROP COLUMN created_by; ALTER TABLE app_user DROP COLUMN created_by; ALTER TABLE bank_reconciliation DROP COLUMN created_by; ALTER TABLE bank_reconciliation_match DROP COLUMN created_by; ALTER TABLE bank_transaction DROP COLUMN created_by; ALTER TABLE bank_transaction_line DROP COLUMN created_by; ALTER TABLE bill DROP COLUMN created_by; ALTER TABLE bill_line DROP COLUMN created_by; ALTER TABLE bill_payment DROP COLUMN created_by; ALTER TABLE bill_payment_allocation DROP COLUMN created_by; ALTER TABLE company_settings DROP COLUMN created_by; ALTER TABLE contact DROP COLUMN created_by; ALTER TABLE contact_address DROP COLUMN created_by; ALTER TABLE contact_bank_account DROP COLUMN created_by; ALTER TABLE contact_person DROP COLUMN created_by; ALTER TABLE cost_layer DROP COLUMN created_by; ALTER TABLE cost_layer_consumption DROP COLUMN created_by; ALTER TABLE currency DROP COLUMN created_by; ALTER TABLE document_sequence DROP COLUMN created_by; ALTER TABLE exchange_rate DROP COLUMN created_by; ALTER TABLE invoice DROP COLUMN created_by; ALTER TABLE invoice_line DROP COLUMN created_by; ALTER TABLE invoice_payment DROP COLUMN created_by; ALTER TABLE invoice_payment_allocation DROP COLUMN created_by; ALTER TABLE item DROP COLUMN created_by; ALTER TABLE item_category DROP COLUMN created_by; ALTER TABLE journal_entry DROP COLUMN created_by; ALTER TABLE journal_entry_line DROP COLUMN created_by; ALTER TABLE landed_cost_allocation DROP COLUMN created_by; ALTER TABLE landed_cost_allocation_line DROP COLUMN created_by; ALTER TABLE payment_term DROP COLUMN created_by; ALTER TABLE stock_balance DROP COLUMN created_by; ALTER TABLE stock_movement DROP COLUMN created_by; ALTER TABLE unit DROP COLUMN created_by; ALTER TABLE unit_group DROP COLUMN created_by; ALTER TABLE warehouse DROP COLUMN created_by;
