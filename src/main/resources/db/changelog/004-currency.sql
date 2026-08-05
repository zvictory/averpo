--liquibase formatted sql

--changeset averpo:006-currency
CREATE TABLE currency (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    code       VARCHAR(3) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    symbol     VARCHAR(8),
    active     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_currency_code UNIQUE (code)
);
--rollback DROP TABLE currency;

--changeset averpo:007-currency-seed
-- Id'лар олдиндан генерацияланган UUIDv7 (вақт тартиби сақланади)
INSERT INTO currency (id, code, name, symbol, active) VALUES
    ('019f3321-a251-79d2-b8e8-4eb240e406a6', 'UZS', 'Ўзбек сўми', 'сўм', true),
    ('019f3321-a322-7e61-ba57-f5d31520ebfa', 'USD', 'АҚШ доллари', '$', true),
    ('019f3321-a322-731d-8e0e-eea8c0787f46', 'EUR', 'Евро', '€', false),
    ('019f3321-a322-7a51-922f-d538d5774849', 'RUB', 'Россия рубли', '₽', false),
    ('019f3321-a326-7b60-a1a5-201ec03fdaa1', 'GBP', 'Фунт стерлинг', '£', false),
    ('019f3321-a327-76ba-9132-056adb2ddb6d', 'KZT', 'Қозоқ тенгеси', '₸', false),
    ('019f3321-a328-7f47-ab8c-00d6d26bc0fa', 'CNY', 'Хитой юани', '¥', false);
--rollback DELETE FROM currency WHERE code IN ('UZS','USD','EUR','RUB','GBP','KZT','CNY');
