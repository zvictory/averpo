--liquibase formatted sql

--changeset averpo:057-app-user-profile
-- Профиль шахсий майдонлари (Arbitr-101, docs/modules/user-profile.md
-- 1 ва 4 бўлимлар): ҳаммаси nullable/ихтиёрий - login username ва парол
-- ўзгармайди, булар фақат маълумот. profile_image_id → attachment
-- (аватар: мавжуд Attachment инфраси, disk storage қайта ишлатилади);
-- employee_contact_id → contact (app_user'ни ходимга улаш, 4-бўлим -
-- payroll employee_id нақши: DB FK, JPA'да оддий UUID). Иккала FK
-- ON DELETE SET NULL: расм ёки контакт ўчса login синмайди
-- (аудит излари сақланади, CoA/Contact «ўчириш йўқ» қоидаси нақши).
ALTER TABLE app_user ADD COLUMN email VARCHAR(255);
ALTER TABLE app_user ADD COLUMN gender VARCHAR(10);
ALTER TABLE app_user ADD COLUMN birthdate DATE;
ALTER TABLE app_user ADD COLUMN phone VARCHAR(50);
ALTER TABLE app_user ADD COLUMN profile_image_id UUID;
ALTER TABLE app_user ADD COLUMN employee_contact_id UUID;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_profile_image
    FOREIGN KEY (profile_image_id) REFERENCES attachment (id) ON DELETE SET NULL;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_employee_contact
    FOREIGN KEY (employee_contact_id) REFERENCES contact (id) ON DELETE SET NULL;
-- BR-USR-015: битта EMPLOYEE контакт фақат битта ФАОЛ app_user'да
-- (1:1 ихтиёрий) - бир ходим икки login'га уланмайди. Partial unique:
-- NULL'лар чекланмайди, нофаол (active=false) user контактни банд
-- қилмайди (ходим бошқа фаол login'га ўтиши мумкин).
CREATE UNIQUE INDEX uq_app_user_employee_contact ON app_user (employee_contact_id)
    WHERE employee_contact_id IS NOT NULL AND active;
--rollback DROP INDEX uq_app_user_employee_contact;
--rollback ALTER TABLE app_user DROP CONSTRAINT fk_app_user_employee_contact;
--rollback ALTER TABLE app_user DROP CONSTRAINT fk_app_user_profile_image;
--rollback ALTER TABLE app_user DROP COLUMN employee_contact_id;
--rollback ALTER TABLE app_user DROP COLUMN profile_image_id;
--rollback ALTER TABLE app_user DROP COLUMN phone;
--rollback ALTER TABLE app_user DROP COLUMN birthdate;
--rollback ALTER TABLE app_user DROP COLUMN gender;
--rollback ALTER TABLE app_user DROP COLUMN email;
