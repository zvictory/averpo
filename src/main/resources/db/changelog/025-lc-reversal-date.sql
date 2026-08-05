--liquibase formatted sql

--changeset averpo:025-01-lc-reversal-date
-- Landed cost сторно санаси (docs/modules/reports.md, inventory
-- valuation): «санага» қиймат тиклашда тақсимот улуши қайси кундан
-- кучда эмаслигини билиш керак - status'нинг ўзи давр ичидаги ҳолатни
-- айтмайди. Мавжуд REVERSED қаторларга updated_at санаси ёзилади
-- (сторно айнан шу пайтда бўлган - эски маълумот учун энг яқин манба).
ALTER TABLE landed_cost_allocation ADD COLUMN reversal_date DATE;

UPDATE landed_cost_allocation
SET reversal_date = (updated_at AT TIME ZONE 'UTC')::date
WHERE status = 'REVERSED' AND reversal_date IS NULL;
--rollback ALTER TABLE landed_cost_allocation DROP COLUMN reversal_date;
