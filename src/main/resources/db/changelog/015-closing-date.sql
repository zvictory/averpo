--liquibase formatted sql

--changeset averpo:015-01-company-settings-closing-date
-- QBO услубидаги давр ёпилиши (docs/modules/closing-date.md):
-- шу санага тенг ёки олдинги санага янги GL ҳаракати тақиқ (BR-LED-020).
-- NULL - қулф йўқ. Тўлиқ fiscal_period жадвали атайлаб олинмади -
-- керак бўлса кейин кенгайтирилади.
ALTER TABLE company_settings ADD COLUMN closing_date DATE;
--rollback ALTER TABLE company_settings DROP COLUMN closing_date;
