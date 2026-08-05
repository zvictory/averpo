--liquibase formatted sql

--changeset averpo:060-01-stock-adjustment-external-ref
-- Ташқи ҳужжат рақами (Arbitr-109, фойдаланувчи талаби): қоғоз акт/
-- дафтар рақамини актга боғлаш (QBO «Reference no.» қолипи). Ихтиёрий -
-- nullable; GL/movement мантиғига тегмайди, соф аудит майдони.
ALTER TABLE stock_adjustment ADD COLUMN external_ref VARCHAR(50);
--rollback ALTER TABLE stock_adjustment DROP COLUMN external_ref;

--changeset averpo:060-02-stock-transfer-external-ref
ALTER TABLE stock_transfer ADD COLUMN external_ref VARCHAR(50);
--rollback ALTER TABLE stock_transfer DROP COLUMN external_ref;
