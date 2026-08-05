--liquibase formatted sql

--changeset averpo:051-01-returns-family-rate-check
-- Arbitr-076 (Dilnoza-002): асосий ҳужжатларда курс DB даражасида
-- кафолатланган (invoice/bill/bank/estimate/PO: CHECK exchange_rate > 0),
-- returns/чек оиласида эса фақат service гарови эди - темир қоида 4
-- (ledger home валютада балансланади) замини DB инвариантисиз қоларди.
-- Мавжуд қаторлар service орқали текширилиб келган (0/манфий йўқ) -
-- ADD CONSTRAINT тоза ўтади.
ALTER TABLE sales_receipt ADD CONSTRAINT ck_sales_receipt_rate_positive
    CHECK (exchange_rate > 0);
ALTER TABLE credit_memo ADD CONSTRAINT ck_credit_memo_rate_positive
    CHECK (exchange_rate > 0);
ALTER TABLE vendor_credit ADD CONSTRAINT ck_vendor_credit_rate_positive
    CHECK (exchange_rate > 0);
ALTER TABLE refund_receipt ADD CONSTRAINT ck_refund_receipt_rate_positive
    CHECK (exchange_rate > 0);
--rollback ALTER TABLE sales_receipt DROP CONSTRAINT ck_sales_receipt_rate_positive; ALTER TABLE credit_memo DROP CONSTRAINT ck_credit_memo_rate_positive; ALTER TABLE vendor_credit DROP CONSTRAINT ck_vendor_credit_rate_positive; ALTER TABLE refund_receipt DROP CONSTRAINT ck_refund_receipt_rate_positive;

--changeset averpo:051-02-sales-receipt-line-price-precision
-- Arbitr-076 (Dilnoza-003): sales_receipt «invoice кўзгуси» - unit_price
-- ҳам invoice_line билан бир хил NUMERIC(24,12) бўлиши керак эди
-- (кенгайтириш - мавжуд қиймат йўқолмайди). CM/VC/RR оиласи 19,4 да
-- ОНГЛИ қолади: қайтармаларда юқори нарх аниқлиги кам керак.
ALTER TABLE sales_receipt_line ALTER COLUMN unit_price TYPE NUMERIC(24,12);
--rollback ALTER TABLE sales_receipt_line ALTER COLUMN unit_price TYPE NUMERIC(19,4);

--changeset averpo:051-03-payroll-gross-check
-- Arbitr-076 (Asrorxoja-014б): payroll.md:93 специ gross'га «numeric(19,4)
-- NOT NULL > 0» ваъда қилади - 044:48 да CHECK тушиб қолган эди. net'га
-- CHECK ҚЎЙИЛМАЙДИ: манфий net гарови service қатламида, аниқ BR хабари
-- билан (Arbitr-071 банд 2).
ALTER TABLE payroll_run_line ADD CONSTRAINT ck_prl_gross_positive
    CHECK (gross > 0);
--rollback ALTER TABLE payroll_run_line DROP CONSTRAINT ck_prl_gross_positive;
