--liquibase formatted sql

--changeset averpo:050-01-audit-user-agent
-- Аудит қамрови кенгайиши (Arbitr-062): «қайси client'дан» саволига жавоб -
-- audit_event'га User-Agent устуни. NULL рухсатли: фон жараёнларда (auto-init,
-- scheduler) web контексти йўқ. 255 чегара - хом UA қаторлари узунроқ бўлса
-- илова ёзишдан олдин қирқади (AuditEvent конструктори).
ALTER TABLE audit_event ADD COLUMN user_agent VARCHAR(255);
--rollback ALTER TABLE audit_event DROP COLUMN user_agent;
