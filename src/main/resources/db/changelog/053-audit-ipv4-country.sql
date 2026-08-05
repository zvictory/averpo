--liquibase formatted sql

--changeset averpo:053-01-audit-ipv4-country
-- Аудит ўқилиши полиши (Arbitr-091): IPv6 уланишларда Cloudflare берадиган
-- синтетик Pseudo IPv4 (Cf-Pseudo-IPv4, 240.0.0.0/4 - мижознинг реал IPv4'и
-- ЭМАС, таниб олиш/боғлаш учун) ва давлат коди (CF-IPCountry, ISO 3166-1
-- alpha-2). Иккиси ҳам NULL рухсатли: dev муҳитда CF header'лар йўқ, фон
-- жараёнларда web контексти йўқ, эски ёзувлар қайта ёзилмайди. Чегарадан
-- узун қийматни илова ёзишдан олдин қирқади (AuditEvent конструктори).
ALTER TABLE audit_event ADD COLUMN ip_v4 VARCHAR(15);
ALTER TABLE audit_event ADD COLUMN country VARCHAR(2);
--rollback ALTER TABLE audit_event DROP COLUMN ip_v4;
--rollback ALTER TABLE audit_event DROP COLUMN country;
