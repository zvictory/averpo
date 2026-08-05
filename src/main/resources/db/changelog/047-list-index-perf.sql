--liquibase formatted sql

--changeset averpo:047-01-jel-contact-index
-- Statement/register мижоз кесими (Arbitr-049, Beruniy-026/029): AR
-- контрол счётининг contact_id бўйича филтри аввал бутун
-- journal_entry_line'ни сканирларди. (account_id, contact_id) композит
-- partial индекс - contact_id тўлдирилган (AR/AP subledger) сатрларгагина,
-- индекс кичик қолади.
CREATE INDEX idx_jel_contact ON journal_entry_line(account_id, contact_id)
    WHERE contact_id IS NOT NULL;
--rollback DROP INDEX idx_jel_contact;

--changeset averpo:047-02-returns-lookup-indexes
-- Returns reverse-lookup (Arbitr-049, Beruniy-034): invoice/bill кўришлари
-- findByInvoiceId/findByBillId қилади, лекин composite unique'нинг
-- иккинчи устуни (invoice_id/bill_id) prefix эмас - тўлиқ скан. Тўрт
-- нишонли индекс: application жадвалларида тўлиқ, ихтиёрий FK устунларда
-- (credit_memo/vendor_credit) partial.
CREATE INDEX idx_credit_application_invoice ON credit_application(invoice_id);
CREATE INDEX idx_credit_memo_invoice ON credit_memo(invoice_id)
    WHERE invoice_id IS NOT NULL;
CREATE INDEX idx_vc_application_bill ON vendor_credit_application(bill_id);
CREATE INDEX idx_vendor_credit_bill ON vendor_credit(bill_id)
    WHERE bill_id IS NOT NULL;
--rollback DROP INDEX idx_credit_application_invoice;
--rollback DROP INDEX idx_credit_memo_invoice;
--rollback DROP INDEX idx_vc_application_bill;
--rollback DROP INDEX idx_vendor_credit_bill;
