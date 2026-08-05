--liquibase formatted sql

--changeset averpo:052-01-user-roles-migration
-- Роль тизими миграцияси (DEC-092, docs/modules/user-roles.md):
-- эски 3 роль янги 8 роллик тўпламга мапланади. СХЕМА ЎЗГАРМАЙДИ
-- (role VARCHAR(20) STRING enum) - фақат мавжуд маълумот UPDATE'лари.
-- ADMIN → SUPER_ADMIN (тўлиқ ҳуқуқ ўзгармайди); ACCOUNTANT номи
-- сақланади (лекин ҳуқуқи энди GL/period close'сиз - кенг ҳуқуқ керак
-- бўлса deploy'дан кейин фойдаланувчи қўлда CHIEF_ACCOUNTANT'га
-- кўтаради, миграция default'и кам ҳуқуқ - хавфсиз томон);
-- VIEWER → VIEWER_AUDITOR (фақат кўриш ўзгармайди).
-- ⚠ Жонли серверда реал admin бор - бу UPDATE уни SUPER_ADMIN қилади
-- (deploy'дан олдин текшириладиган банд, spec «Entity ва DB»).
UPDATE app_user SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN';
UPDATE app_user SET role = 'VIEWER_AUDITOR' WHERE role = 'VIEWER';
--rollback UPDATE app_user SET role = 'ADMIN' WHERE role = 'SUPER_ADMIN';
--rollback UPDATE app_user SET role = 'VIEWER' WHERE role = 'VIEWER_AUDITOR';
