--liquibase formatted sql

--changeset averpo:059-01-telegram-settings
-- Telegram бот созламаси (docs/modules/user-profile.md 3-бўлим,
-- Arbitr-103): singleton қатор - биз бир-tenant, ҳар компания ЎЗ ботини
-- яратади (@BotFather), умумий платформа боти йўқ.
--
-- token_enc - ШИФРЛАНГАН токен (AES-GCM, base64(IV||CT); арбитр қарори
-- 2026-07-17): очиқ матн ЙЎҚ - калит базада эмас, AVERPO_SECRET_KEY
-- env'да, шунда база dump/захираси (масалан миграция олди backup'и)
-- bearer креденшл'ни ошкор қилмайди. Устун номи атайлаб «_enc» билан -
-- бу ерга очиқ токен ёзиб қўйиш кейинги ўқувчига дарҳол хато кўринсин.
-- TEXT: шифрланган матн узунлиги токен узунлигидан катта (IV + tag + base64).
--
-- update_offset - getUpdates курсори: рестартдан кейин poller ўша
-- жойдан давом этади (эски update'лар қайта ишланмайди).
CREATE TABLE telegram_settings (
    id            UUID PRIMARY KEY,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    token_enc     TEXT,
    bot_username  VARCHAR(64),
    update_offset BIGINT NOT NULL DEFAULT 0
);
--rollback DROP TABLE telegram_settings;

--changeset averpo:059-02-app-user-telegram
-- Фойдаланувчининг уланган Telegram ҳисоби ва улаш коди.
-- chat_id BIGINT - Telegram id'лари 32-бит чегарасидан ошган (расмий
-- тавсия: 52-битгача сиғадиган тур).
-- link_code/link_expires_at - бир марталик код (TTL 10 дақиқа) ЖАДВАЛСИЗ:
-- «бир фойдаланувчида бир код» (карта тузоқ 4) устун устига ёзиш билан
-- конструкциядан келиб чиқади, тозалаш вазифаси керак эмас; код
-- ишлатилгач дарҳол NULL'га тушади - қайта ишламайди.
ALTER TABLE app_user ADD COLUMN telegram_chat_id BIGINT;
ALTER TABLE app_user ADD COLUMN telegram_username VARCHAR(64);
ALTER TABLE app_user ADD COLUMN telegram_link_code VARCHAR(64);
ALTER TABLE app_user ADD COLUMN telegram_link_expires_at TIMESTAMPTZ;
-- Poller кодни ном бўйича қидиради - икки фойдаланувчида бир хил код
-- (эҳтимоли ~0, лекин) ноаниқлик берарди: partial unique буни ёзувда
-- ЯҚҚОЛ тўхтатади. Бизнес қоида эмас - яхлитлик гарови.
CREATE UNIQUE INDEX uq_app_user_telegram_link_code
    ON app_user (telegram_link_code) WHERE telegram_link_code IS NOT NULL;
--rollback DROP INDEX uq_app_user_telegram_link_code;
--rollback ALTER TABLE app_user DROP COLUMN telegram_link_expires_at;
--rollback ALTER TABLE app_user DROP COLUMN telegram_link_code;
--rollback ALTER TABLE app_user DROP COLUMN telegram_username;
--rollback ALTER TABLE app_user DROP COLUMN telegram_chat_id;
