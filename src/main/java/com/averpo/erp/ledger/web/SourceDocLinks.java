package com.averpo.erp.ledger.web;

import java.util.UUID;

/**
 * JE манба ҳужжати кўриш саҳифасига URL (DEC-063): sourceModule →
 * URL prefix харитаси. АТАЙЛАБ ledger.web ичида ЛОКАЛ (қоида №6: ledger
 * бошқа модулга компиляция боғланиши қила олмайди - URL string'лар эса
 * боғланиш эмас, шунчаки манзил битими). Экранлар (JE кўриши, statement)
 * шу орқали «Ҳужжатни очиш» линкини ясайди.
 *
 * <p>Харитада ЙЎҚ турлар (null қайтади - линк чиқмайди): MANUAL ва
 * OPENING_BALANCE (ҳужжат саҳифаси йўқ); allocation/курс фарқи JE'лари
 * (RECEIPT_ALLOCATION, PAYMENT_ALLOCATION, CREDIT_APPLICATION,
 * VENDOR_CREDIT_APPLICATION) - sourceDocumentId аллокация ёзувига ишора
 * қилади, унинг ўз саҳифаси йўқ; INVENTORY - movement'нинг алоҳида
 * кўриш саҳифаси йўқ.
 */
public final class SourceDocLinks {

    private SourceDocLinks() {
    }

    /**
     * Манба ҳужжат кўриш саҳифаси URL'и ёки null (саҳифасиз тур/қўлда
     * проводка). Prefix'лар тегишли controller @RequestMapping'ларига
     * мос туради - манзил ўзгарса шу харита ҳам янгиланади.
     */
    public static String url(String sourceModule, UUID documentId) {
        if (sourceModule == null || documentId == null) {
            return null;
        }
        String prefix = switch (sourceModule) {
            case "INVOICE" -> "/invoices/";
            case "INVOICE_PAYMENT" -> "/invoice-payments/";
            case "CREDIT_MEMO" -> "/credit-memos/";
            case "SALES_RECEIPT" -> "/sales-receipts/";
            case "REFUND_RECEIPT" -> "/refund-receipts/";
            case "BILL" -> "/bills/";
            case "BILL_PAYMENT" -> "/payments/";
            case "VENDOR_CREDIT" -> "/vendor-credits/";
            case "LANDED_COST" -> "/landed-costs/";
            case "BANK_TXN" -> "/bank-transactions/";
            case "PAYROLL_RUN" -> "/payroll/";
            case "PAYROLL_PAYMENT" -> "/payroll/payments/";
            default -> null;
        };
        return prefix == null ? null : prefix + documentId;
    }
}
