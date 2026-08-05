--liquibase formatted sql

--changeset averpo:029-01-app-user-lockout
-- BR-USR-009 (Eldor-002): login lockout ҳолати user'нинг ўзида туради -
-- 5 кетма-кет хато уриниш failed_attempts'да саналади, locked_until'гача
-- login тақиқ (UTC, темир қоида №12). Муваффақиятли киришда иккаласи
-- нолланади (LoginAttemptListener).
ALTER TABLE app_user ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMPTZ;
--rollback ALTER TABLE app_user DROP COLUMN failed_attempts; ALTER TABLE app_user DROP COLUMN locked_until;
