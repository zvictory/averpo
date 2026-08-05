--liquibase formatted sql

--changeset averpo:061-01-list-filter-indexes
-- Ҳужжат рўйхати филтрлари учун иккиламчи индекслар (DEC-110 индекс
-- аудити). 041/046 (sales_receipt/refund_receipt/vendor_credit) битта ҳам
-- иккиламчи индекссиз чиққан; credit_memo/payroll_run/bank_transaction'да
-- ҳам рўйхат филтрлари қопланмаган. Ҳар рўйхат service'и (068 ListSpecs
-- нақши) айнан шу устунлар бўйича Specification қуради: контакт кесими,
-- сана диапазони, статус. Ҳозир маълумот кичик - оғриқ йўқ; продукт ўсимда
-- рўйхат сканлари секинлашади, индекслар арзон - ҳозир қўйилади. Омбор
-- (stock_movement) индекслари кейинги картага (арбитр қарори: фақат А қисм).

-- Сотув чеки (SalesReceiptService Specification: customer_id/sr_date/status)
CREATE INDEX idx_sales_receipt_customer ON sales_receipt(customer_id);
CREATE INDEX idx_sales_receipt_date ON sales_receipt(sr_date);
CREATE INDEX idx_sales_receipt_status ON sales_receipt(status);

-- Пул қайтариш чеки (RefundReceiptService: customer_id/rr_date/status)
CREATE INDEX idx_refund_receipt_customer ON refund_receipt(customer_id);
CREATE INDEX idx_refund_receipt_date ON refund_receipt(rr_date);
CREATE INDEX idx_refund_receipt_status ON refund_receipt(status);

-- Таъминотчи кредит-нотаси (VendorCreditService: vendor_id/vc_date/status;
-- мавжуд idx_vendor_credit_bill фақат bill_id reverse-lookup учун)
CREATE INDEX idx_vendor_credit_vendor ON vendor_credit(vendor_id);
CREATE INDEX idx_vendor_credit_date ON vendor_credit(vc_date);
CREATE INDEX idx_vendor_credit_status ON vendor_credit(status);

-- Мижоз кредит-нотаси (CreditMemoService: customer_id/cm_date/status;
-- мавжуд idx_credit_memo_invoice фақат invoice_id reverse-lookup учун)
CREATE INDEX idx_credit_memo_customer ON credit_memo(customer_id);
CREATE INDEX idx_credit_memo_date ON credit_memo(cm_date);
CREATE INDEX idx_credit_memo_status ON credit_memo(status);

-- Иш ҳақи ҳисоблаши (PayrollRunService: run_date/status; мавжуд
-- ux_payroll_run_period_posted фақат period бўйича partial-unique)
CREATE INDEX idx_payroll_run_date ON payroll_run(run_date);
CREATE INDEX idx_payroll_run_status ON payroll_run(status);

-- Банк транзакцияси (BankTransactionService: status/contact_id филтрлари;
-- type+сана - Expense экрани доим type филтри билан юради. Мавжуд
-- idx_bank_txn_date [txn_date ялғиз] type-етакчи композитни қопламайди)
CREATE INDEX idx_bank_txn_status ON bank_transaction(status);
CREATE INDEX idx_bank_txn_contact ON bank_transaction(contact_id);
CREATE INDEX idx_bank_txn_type_date ON bank_transaction(type, txn_date);
--rollback DROP INDEX idx_sales_receipt_customer;
--rollback DROP INDEX idx_sales_receipt_date;
--rollback DROP INDEX idx_sales_receipt_status;
--rollback DROP INDEX idx_refund_receipt_customer;
--rollback DROP INDEX idx_refund_receipt_date;
--rollback DROP INDEX idx_refund_receipt_status;
--rollback DROP INDEX idx_vendor_credit_vendor;
--rollback DROP INDEX idx_vendor_credit_date;
--rollback DROP INDEX idx_vendor_credit_status;
--rollback DROP INDEX idx_credit_memo_customer;
--rollback DROP INDEX idx_credit_memo_date;
--rollback DROP INDEX idx_credit_memo_status;
--rollback DROP INDEX idx_payroll_run_date;
--rollback DROP INDEX idx_payroll_run_status;
--rollback DROP INDEX idx_bank_txn_status;
--rollback DROP INDEX idx_bank_txn_contact;
--rollback DROP INDEX idx_bank_txn_type_date;
