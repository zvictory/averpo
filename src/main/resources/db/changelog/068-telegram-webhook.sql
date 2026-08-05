--liquibase formatted sql

--changeset averpo:068-telegram-webhook
-- Telegram webhook сири (docs/modules/user-profile.md 3-бўлим,
-- DEC-138): prod'да Telegram янгиликларни POST /telegram/webhook'га
-- юборади, ҳар сўровда X-Telegram-Bot-Api-Secret-Token header'ини
-- қўшади. Шу header сақланган сир билан таққосланиб «фақат Telegram»
-- кириши гарантланади (permitAll endpoint - секрет ягона ҳимоя).
--
-- webhook_secret_enc - сирнинг ШИФРЛАНГАН қиймати (token_enc билан бир
-- хил ҳимоя: AES-GCM, base64(IV||CT), калит AVERPO_SECRET_KEY env'да).
-- Секретни registrar SecureRandom билан яратиб шифрлаб сақлайди - очиқ
-- матн базада ЙЎҚ, dump/захира уни ошкор қилмайди. Nullable: webhook
-- ҳали рўйхатдан ўтмаган (дев polling ёки токенсиз prod) ҳолда null.
ALTER TABLE telegram_settings ADD COLUMN webhook_secret_enc TEXT;
--rollback ALTER TABLE telegram_settings DROP COLUMN webhook_secret_enc;
