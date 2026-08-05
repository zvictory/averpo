package com.averpo.erp.search.service;

import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.Fmt;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Глобал қидирув (QBO Navigate паритети, docs/modules/global-search.md):
 * битта майдондан ҳужжат рақами, контакт, товар, счёт ва экран/ҳисобот
 * номи бўйича бутун тизимни қидиради.
 *
 * <p>Архитектура: ҳар манбага биттадан ЕНГИЛ SELECT (JdbcClient, LIMIT 5) -
 * {@code LedgerDashboardService} хом-SQL прецеденти (read-only). Бошқа
 * модуллар repository'сига тегилмайди (темир қоида №6): натижа ҳамма
 * рўйхат экрани кўрсатадиган очиқ маълумот, янги ёзиш йўқ, шунинг учун
 * янги BR код керак эмас. Инъекцияга қарши - барча сўров SQL параметр
 * binding'и билан ({@code ILIKE :q}).
 *
 * <p>MVP кўлами: рақам substring / ном ILIKE. Full-text, trgm индекс ва
 * fuzzy - phase-2 (spec «ҚИЛИНМАЙДИ»).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GlobalSearchService {

    /** Сўров нормаси: шундан қисқа бўлса умуман қидирилмайди (spec). */
    private static final int MIN_QUERY_LENGTH = 2;

    /** Ҳар манба гуруҳида кўпи билан шунча натижа (spec: 5). */
    private static final int GROUP_LIMIT = 5;

    /** Sublabel бўлакларини ажратгич: « · » (uzun tire эмас). */
    private static final String SEP = " · ";

    /** NBSP - пул ва валюта коди орасида (Fmt конвенцияси). */
    private static final String NBSP = " ";

    /**
     * Битта ҳужжат манбаси (жадвал) конфигурацияси - {@link #DOC_SOURCES}
     * рўйхатидан UNION SELECT қурилади. Ҳужжат турлари кўп, лекин натижа
     * гуруҳи битта («Ҳужжатлар»), шунинг учун ҳаммаси битта UNION'да
     * йиғилиб умумий 5 тагача қисқартирилади.
     *
     * @param table      жадвал номи
     * @param numberCol  ҳужжат рақами устуни (ILIKE қилинади)
     * @param extraSearchCol қўшимча қидирув устуни ёки null (AUD-016:
     *                   bank_transaction ref_no - банк ҳужжат ҳаваласи ҳам
     *                   рақамдек қидирилади)
     * @param contactFk  контакт FK устуни ёки null (JE/payroll - сарлавҳада йўқ)
     * @param dateCol    сана устуни (натижа тартиби учун)
     * @param hasCurrency currency_id FK борми (сумма валютаси коди учун)
     * @param totalCol   сумма устуни ёки null (JE/payroll_run - йўқ):
     *                   ҳужжатларда {@code total}, тўлов/LC оиласида
     *                   {@code total_amount} (DEC-074 - жадвалдан аниқланди)
     * @param urlPrefix  кўриш экрани префикси, кетига {@code id} қўшилади
     */
    private record DocSource(String table, String numberCol, String extraSearchCol,
                             String contactFk, String dateCol, boolean hasCurrency,
                             String totalCol, String urlPrefix) {
    }

    /**
     * Қидириладиган ҳужжат жадваллари (spec «Ҳужжатлар» кўлами, DEC-074
     * билан тўлдирилган: тўловлар RCPT-/PAY-, банк txn BT- + ref_no, landed
     * cost LC- - QBO Navigate паритети). Тартиб муҳим эмас - натижа сана
     * бўйича сараланади. Route'лар айнан тегишли view controller'ларнинг
     * {@code GET /.../{id}} манзили.
     */
    private static final List<DocSource> DOC_SOURCES = List.of(
            new DocSource("invoice", "invoice_number", null, "customer_id", "invoice_date", true, "total", "/invoices/"),
            new DocSource("bill", "bill_number", null, "vendor_id", "bill_date", true, "total", "/bills/"),
            new DocSource("credit_memo", "cm_number", null, "customer_id", "cm_date", true, "total", "/credit-memos/"),
            new DocSource("estimate", "estimate_number", null, "customer_id", "estimate_date", true, "total", "/estimates/"),
            new DocSource("purchase_order", "po_number", null, "vendor_id", "po_date", true, "total", "/purchase-orders/"),
            new DocSource("vendor_credit", "vc_number", null, "vendor_id", "vc_date", true, "total", "/vendor-credits/"),
            new DocSource("refund_receipt", "rr_number", null, "customer_id", "rr_date", true, "total", "/refund-receipts/"),
            new DocSource("bank_transaction", "txn_number", "ref_no", "contact_id", "txn_date", true, "total", "/bank-transactions/"),
            new DocSource("sales_receipt", "sr_number", null, "customer_id", "sr_date", true, "total", "/sales-receipts/"),
            new DocSource("invoice_payment", "receipt_number", null, "customer_id", "payment_date", true, "total_amount", "/invoice-payments/"),
            new DocSource("bill_payment", "payment_number", null, "vendor_id", "payment_date", true, "total_amount", "/payments/"),
            new DocSource("landed_cost_allocation", "allocation_number", null, null, "allocation_date", false, "total_amount", "/landed-costs/"),
            new DocSource("payroll_run", "run_number", null, null, "run_date", false, null, "/payroll/"),
            new DocSource("payroll_payment", "payp_number", null, null, "payment_date", false, "total", "/payroll/payments/"),
            new DocSource("journal_entry", "entry_number", null, null, "entry_date", false, null, "/journal-entries/"));

    /** Ҳужжатлар UNION SQL'и - манбалар ўзгармас, бир марта қурилади. */
    private static final String DOCUMENT_SQL = buildDocumentSql();

    /** Хом SELECT'лар учун JdbcClient (Hibernate четлаб ўтилади). */
    private final JdbcClient jdbc;

    /** Экран реестри бўйича қидирув (статик). */
    private final ScreenRegistry screenRegistry;

    /** Сумма валютасиз (home) ҳужжатлар учун home валюта коди. */
    private final CompanySettingsService settings;

    /**
     * Бутун тизим бўйича қидирув.
     *
     * @param rawQuery фойдаланувчи сўрови (trim қилинади)
     * @param isAdmin  экран реестри роль филтри (Созламалар фақат ADMIN)
     * @return гуруҳланган натижа; сўров {@value #MIN_QUERY_LENGTH} белгидан
     *         қисқа бўлса бўш (умуман DB'га борилмайди)
     */
    public SearchResults search(String rawQuery, boolean isAdmin) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            return SearchResults.empty();
        }
        return new SearchResults(
                searchDocuments(q),
                searchContacts(q),
                searchItems(q),
                searchAccounts(q),
                screenRegistry.search(q, isAdmin));
    }

    /** Ҳужжатлар: барча тур бўйича UNION, сана десц, умумий 5 та. */
    private List<SearchHit> searchDocuments(String q) {
        String homeCode = settings.homeCurrency();
        return jdbc.sql(DOCUMENT_SQL)
                .param("q", like(q))
                .query((rs, rowNum) -> {
                    BigDecimal total = rs.getBigDecimal("total");
                    String currency = rs.getString("currency_code");
                    return new SearchHit(
                            rs.getString("num"),
                            documentSublabel(rs.getString("contact_name"),
                                    rs.getObject("doc_date", LocalDate.class),
                                    total, currency == null ? homeCode : currency),
                            rs.getString("url"));
                })
                .list();
    }

    /** Контактлар: ном/компания бўйича; URL контакт типига қараб. */
    private List<SearchHit> searchContacts(String q) {
        return jdbc.sql("""
                        SELECT id, display_name, company_name, type
                        FROM contact
                        WHERE display_name ILIKE :q OR company_name ILIKE :q
                        ORDER BY display_name
                        LIMIT :lim
                        """)
                .param("q", like(q))
                .param("lim", GROUP_LIMIT)
                .query((rs, rowNum) -> new SearchHit(
                        rs.getString("display_name"),
                        blankToNull(rs.getString("company_name")),
                        contactUrl(rs.getString("type"), rs.getString("id"))))
                .list();
    }

    /** Товарлар: ном/SKU бўйича → таҳрир (drawer) экрани. */
    private List<SearchHit> searchItems(String q) {
        return jdbc.sql("""
                        SELECT id, name, sku
                        FROM item
                        WHERE name ILIKE :q OR sku ILIKE :q
                        ORDER BY name
                        LIMIT :lim
                        """)
                .param("q", like(q))
                .param("lim", GROUP_LIMIT)
                .query((rs, rowNum) -> new SearchHit(
                        rs.getString("name"),
                        blankToNull(rs.getString("sku")),
                        "/items/" + rs.getString("id") + "/edit"))
                .list();
    }

    /** Счётлар: ном/код бўйича → счёт тарихи (Account history). */
    private List<SearchHit> searchAccounts(String q) {
        return jdbc.sql("""
                        SELECT id, name, code
                        FROM account
                        WHERE name ILIKE :q OR code ILIKE :q
                        ORDER BY code NULLS LAST, name
                        LIMIT :lim
                        """)
                .param("q", like(q))
                .param("lim", GROUP_LIMIT)
                .query((rs, rowNum) -> new SearchHit(
                        rs.getString("name"),
                        blankToNull(rs.getString("code")),
                        "/accounts/" + rs.getString("id") + "/transactions"))
                .list();
    }

    /**
     * Контакт типидан URL қуради. Мижоз/таъминотчи → КОНТАКТ КАРТОЧКАСИ
     * (кўриш саҳифаси, DEC-002): {@code /customers/{id}} ёки
     * {@code /vendors/{id}}. Ходим → эски таҳрир формаси
     * ({@code /employees/{id}/edit}) - ходим картаси 2-босқич
     * (contact-card.md), карточка саҳифаси йўқ.
     */
    private String contactUrl(String type, String id) {
        return switch (type) {
            case "CUSTOMER" -> "/customers/" + id;
            case "VENDOR" -> "/vendors/" + id;
            default -> "/employees/" + id + "/edit"; // EMPLOYEE - карта йўқ
        };
    }

    /**
     * Ҳужжат sublabel'и: мавжуд бўлаклар « · » билан уланади - контакт
     * номи, сана, сумма (валюта коди билан; пул яланғоч кўрсатилмайди).
     */
    private String documentSublabel(String contact, LocalDate date,
                                    BigDecimal total, String currency) {
        List<String> parts = new ArrayList<>(3);
        if (contact != null && !contact.isBlank()) {
            parts.add(contact);
        }
        if (date != null) {
            parts.add(date.toString());
        }
        if (total != null) {
            parts.add(Fmt.money(total) + NBSP + currency);
        }
        return String.join(SEP, parts);
    }

    /** Бўш/пробел сатрни null'га (шаблон иккиламчи қаторни кўрсатмайди). */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** ILIKE substring намунаси: %query%. */
    private static String like(String query) {
        return "%" + query + "%";
    }

    /**
     * {@link #DOC_SOURCES}'дан UNION ALL SQL'ини қуради: ҳар манба ўзининг
     * рақами (керак бўлса қўшимча устуни - ref_no) бўйича ILIKE, ичкарида
     * LIMIT 5, ташқарида умумий 5. Устунлар тури UNION резолюцияси
     * бузилмаслиги учун аниқ cast қилинади (null'ларда {@code ::text}/
     * {@code ::numeric}).
     */
    private static String buildDocumentSql() {
        List<String> selects = new ArrayList<>(DOC_SOURCES.size());
        for (DocSource s : DOC_SOURCES) {
            String contactExpr = s.contactFk() == null ? "NULL::text" : "c.display_name";
            String totalExpr = s.totalCol() == null ? "NULL::numeric" : "t." + s.totalCol();
            String currencyExpr = s.hasCurrency() ? "cur.code" : "NULL::text";
            StringBuilder joins = new StringBuilder();
            if (s.contactFk() != null) {
                joins.append(" LEFT JOIN contact c ON c.id = t.").append(s.contactFk());
            }
            if (s.hasCurrency()) {
                joins.append(" LEFT JOIN currency cur ON cur.id = t.currency_id");
            }
            String where = "t." + s.numberCol() + " ILIKE :q";
            if (s.extraSearchCol() != null) {
                where = "(" + where + " OR t." + s.extraSearchCol() + " ILIKE :q)";
            }
            selects.add("(SELECT t." + s.numberCol() + " AS num, "
                    + contactExpr + " AS contact_name, "
                    + "t." + s.dateCol() + " AS doc_date, "
                    + totalExpr + " AS total, "
                    + currencyExpr + " AS currency_code, "
                    + "'" + s.urlPrefix() + "' || t.id::text AS url "
                    + "FROM " + s.table() + " t" + joins
                    + " WHERE " + where + " "
                    + "ORDER BY t." + s.dateCol() + " DESC LIMIT " + GROUP_LIMIT + ")");
        }
        return "SELECT num, contact_name, doc_date, total, currency_code, url FROM ("
                + String.join(" UNION ALL ", selects)
                + ") d ORDER BY doc_date DESC LIMIT " + GROUP_LIMIT;
    }
}
