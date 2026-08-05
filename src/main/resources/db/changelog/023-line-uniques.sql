--liquibase formatted sql

--changeset averpo:023-01-line-tables-unique
-- PERF-010: сатр жадвалларида (owner_id, line_no) UNIQUE йўқ эди -
-- journal_entry_line/landed_cost_allocation_line'даги паттерн билан
-- мувофиқлаштирилади. Domain (addLine lines.size()+1) ҳимояси бор,
-- бу DB даражасидаги кафолат: batch import/ташқи скрипт такрор
-- line_no ёзолмайди. Қўлланган 019/021/022 changeset'ларга тегилмайди
-- (checksum identity) - шунинг учун алоҳида файл.
CREATE UNIQUE INDEX ux_bill_line_no ON bill_line(bill_id, line_no);
CREATE UNIQUE INDEX ux_invoice_line_no ON invoice_line(invoice_id, line_no);
CREATE UNIQUE INDEX ux_bank_txn_line_no ON bank_transaction_line(txn_id, line_no);
--rollback DROP INDEX ux_bill_line_no; DROP INDEX ux_invoice_line_no; DROP INDEX ux_bank_txn_line_no;
