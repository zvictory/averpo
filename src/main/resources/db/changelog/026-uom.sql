--liquibase formatted sql

--changeset averpo:026-01-unit-group
-- UoM гуруҳлари (docs/modules/uom.md): бир гуруҳ ичида конверсия,
-- factor бирликнинг ўзида (base'га нисбатан) - жуфт конверсия жадвали
-- атайлаб олинмади (зиддият бўлмасин).
CREATE TABLE unit_group (
    id         UUID PRIMARY KEY,
    version    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    name       VARCHAR(50) NOT NULL,
    CONSTRAINT uq_unit_group_name UNIQUE (name)
);
--rollback DROP TABLE unit_group;

--changeset averpo:026-02-unit-uom-columns
-- Гуруҳсиз бирлик (ҳозиргилар) конверсиясиз ишлайверади: factor 1.
ALTER TABLE unit ADD COLUMN group_id UUID REFERENCES unit_group(id);
ALTER TABLE unit ADD COLUMN factor NUMERIC(24,12) NOT NULL DEFAULT 1;
ALTER TABLE unit ADD COLUMN is_base BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE unit ADD CONSTRAINT ck_unit_factor_positive CHECK (factor > 0);
-- BR-UOM-004: гуруҳда айнан битта base (service ҳам текширади)
CREATE UNIQUE INDEX ux_unit_group_base ON unit (group_id) WHERE is_base;
CREATE INDEX idx_unit_group ON unit (group_id);
--rollback ALTER TABLE unit DROP COLUMN group_id, DROP COLUMN factor, DROP COLUMN is_base;

--changeset averpo:026-03-item-default-units
-- Default харид/сотув бирлиги (BR-ITM-012: base билан бир гуруҳдан);
-- null - base ишлатилади.
ALTER TABLE item ADD COLUMN purchase_unit_id UUID REFERENCES unit(id);
ALTER TABLE item ADD COLUMN sales_unit_id UUID REFERENCES unit(id);
--rollback ALTER TABLE item DROP COLUMN purchase_unit_id, DROP COLUMN sales_unit_id;
