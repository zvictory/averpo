package com.averpo.erp.search;

import com.averpo.erp.search.service.GlobalSearchService;
import com.averpo.erp.search.service.SearchHit;
import com.averpo.erp.search.service.SearchResults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Глобал қидирув service тестлари (docs/modules/global-search.md
 * «Тестлар» 1-4). Дата ҚЎЛДА JdbcClient билан қуйилади - қидирув хом
 * жадвалларни ўқигани учун тест ҳам симметрик тарзда хом ёзади ва seed'га
 * боғланмайди. Ноёб {@code TOKEN} seed маълумотида учрамайди - натижа
 * сони детерминик.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GlobalSearchServiceTest {

    /** Seed'да учрамайдиган ноёб қидирув токени (натижа сони аниқ бўлсин). */
    private static final String TOKEN = "Qwixby";

    @Autowired GlobalSearchService searchService;
    @Autowired JdbcClient jdbc;

    /** Ҳужжат FK'лари учун seed'даги home валюта (UZS) id'си. */
    private UUID uzsId;

    @BeforeEach
    void homeCurrency() {
        uzsId = jdbc.sql("SELECT id FROM currency WHERE code = 'UZS'")
                .query(UUID.class).single();
    }

    /** Тест 1: ҳужжат рақами бўйича топилади - тўғри route + substring. */
    @Test
    void documentsFoundByNumber_routeAndSubstring() {
        UUID customer = insertContact("CUSTOMER", TOKEN + " Savdo", "AlphaCo");
        UUID vendor = insertContact("VENDOR", TOKEN + " Taminot", null);
        insertInvoice("INV-" + TOKEN + "-01", customer, "1200000");
        insertBill("BILL-" + TOKEN + "-02", vendor, "500000");
        insertJournalEntry("JE-" + TOKEN + "-03");

        // Аниқ рақам: invoice топилади, route + sublabel (контакт + валюта коди)
        SearchResults exact = searchService.search("INV-" + TOKEN, false);
        assertThat(exact.documents()).anySatisfy(hit -> {
            assertThat(hit.label()).isEqualTo("INV-" + TOKEN + "-01");
            assertThat(hit.url()).matches("/invoices/.+");
            assertThat(hit.sublabel()).contains(TOKEN + " Savdo").contains("UZS");
        });

        // Substring (рақамнинг қисми) - учала турни ҳам ушлайди
        SearchResults partial = searchService.search(TOKEN, false);
        assertThat(partial.documents()).extracting(SearchHit::url)
                .anyMatch(url -> url.startsWith("/invoices/"))
                .anyMatch(url -> url.startsWith("/bills/"))
                .anyMatch(url -> url.startsWith("/journal-entries/"));
    }

    /** Тест 2: контакт/товар/счёт ном бўйича ILIKE регистрсиз - тўғри route. */
    @Test
    void catalogsFoundCaseInsensitive_withRoutes() {
        insertContact("CUSTOMER", TOKEN + " Mijoz", "ZetaCorp");
        insertContact("VENDOR", TOKEN + " Yetkazuvchi", null);
        insertContact("EMPLOYEE", TOKEN + " Xodim", null);
        insertItem(TOKEN + " Mahsulot", "SKU-" + TOKEN);
        insertAccount(TOKEN + " Hisob", "QWX1");

        // Кичик ҳарфда қидириш ҳам топади (регистрсиз)
        SearchResults r = searchService.search(TOKEN.toLowerCase(), false);
        assertThat(r.contacts()).extracting(SearchHit::url)
                // DEC-002: мижоз/таъминотчи → КОНТАКТ КАРТОЧКАСИ (/edit эмас);
                // ходим → эски таҳрир (ходим картаси 2-босқич)
                .anyMatch(url -> url.matches("/customers/[^/]+"))
                .anyMatch(url -> url.matches("/vendors/[^/]+"))
                .anyMatch(url -> url.matches("/employees/.+/edit"));
        assertThat(r.items()).anySatisfy(hit ->
                assertThat(hit.url()).matches("/items/.+/edit"));
        assertThat(r.accounts()).anySatisfy(hit ->
                assertThat(hit.url()).matches("/accounts/.+/transactions"));

        // Компания номи / SKU / счёт коди бўйича ҳам топилади
        assertThat(searchService.search("zetacorp", false).contacts()).isNotEmpty();
        assertThat(searchService.search("sku-" + TOKEN, false).items()).isNotEmpty();
        assertThat(searchService.search("qwx1", false).accounts()).isNotEmpty();
    }

    /**
     * DEC-074: тўловлар (RCPT-/PAY-), банк txn ref_no ва landed cost
     * (LC-) ҳам «Ҳужжатлар» гуруҳида - QBO Navigate паритети. BT- рақами
     * аввалдан қамровда эди, энди ref_no бўйича ҳам топилади (AUD-016).
     */
    @Test
    void paymentsBankRefNoLandedCost_found() {
        UUID customer = insertContact("CUSTOMER", TOKEN + " Tolovchi", null);
        UUID vendor = insertContact("VENDOR", TOKEN + " Oluvchi", null);
        UUID bank = insertAccount(TOKEN + " Bank", "QWX7");
        insertInvoicePayment("RCPT-" + TOKEN + "-01", customer, bank, "700000");
        insertBillPayment("PAY-" + TOKEN + "-02", vendor, bank, "300000");
        insertBankTransaction("BT-" + TOKEN + "-03", bank, "REF" + TOKEN + "99", "150000");
        insertLandedCost("LC-" + TOKEN + "-04", "90000");

        SearchResults r = searchService.search(TOKEN, false);
        assertThat(r.documents()).extracting(SearchHit::url)
                .anyMatch(url -> url.startsWith("/invoice-payments/"))
                .anyMatch(url -> url.startsWith("/payments/"))
                .anyMatch(url -> url.startsWith("/bank-transactions/"))
                .anyMatch(url -> url.startsWith("/landed-costs/"));

        // ref_no бўйича топиш - лейбл txn рақами, route кўриш экрани
        assertThat(searchService.search("REF" + TOKEN + "99", false).documents())
                .anySatisfy(hit -> {
                    assertThat(hit.label()).isEqualTo("BT-" + TOKEN + "-03");
                    assertThat(hit.url()).matches("/bank-transactions/.+");
                });
    }

    /** Тест 3: ҳар гуруҳда LIMIT 5 (6 мос ёзувда 5 қайтади). */
    @Test
    void groupLimitedToFive() {
        for (int i = 1; i <= 6; i++) {
            insertContact("CUSTOMER", TOKEN + "Lim" + i, null);
        }
        SearchResults r = searchService.search(TOKEN + "Lim", false);
        assertThat(r.contacts()).hasSize(5);
    }

    /** Тест 4: 2 белгидан қисқа/бўш/null сўров - бўш натижа. */
    @Test
    void shortOrBlankQuery_returnsEmpty() {
        insertContact("CUSTOMER", TOKEN + " Single", null);
        assertThat(searchService.search("Q", false).isEmpty()).isTrue();  // 1 белги
        assertThat(searchService.search("  ", false).total()).isZero();   // бўш (trim)
        assertThat(searchService.search(null, false).isEmpty()).isTrue(); // null
    }

    // ---- Дата ёрдамчилари (хом JdbcClient insert) ----

    private UUID insertContact(String type, String displayName, String company) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO contact (id, type, display_name, company_name, active)
                        VALUES (:id, :type, :name, :company, true)
                        """)
                .param("id", id).param("type", type).param("name", displayName)
                .param("company", company).update();
        return id;
    }

    private void insertInvoice(String number, UUID customerId, String total) {
        jdbc.sql("""
                        INSERT INTO invoice (id, invoice_number, customer_id, invoice_date,
                            currency_id, exchange_rate, status, total, total_base)
                        VALUES (:id, :num, :cust, :date, :cur, 1, 'POSTED', :total, :total)
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("cust", customerId).param("date", LocalDate.of(2026, 7, 1))
                .param("cur", uzsId).param("total", new BigDecimal(total)).update();
    }

    private void insertBill(String number, UUID vendorId, String total) {
        jdbc.sql("""
                        INSERT INTO bill (id, bill_number, vendor_id, bill_date,
                            currency_id, exchange_rate, status, total, total_base)
                        VALUES (:id, :num, :vend, :date, :cur, 1, 'POSTED', :total, :total)
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("vend", vendorId).param("date", LocalDate.of(2026, 7, 1))
                .param("cur", uzsId).param("total", new BigDecimal(total)).update();
    }

    private void insertJournalEntry(String number) {
        jdbc.sql("""
                        INSERT INTO journal_entry (id, entry_number, entry_date, status)
                        VALUES (:id, :num, :date, 'POSTED')
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("date", LocalDate.of(2026, 7, 2)).update();
    }

    private void insertItem(String name, String sku) {
        jdbc.sql("""
                        INSERT INTO item (id, type, name, sku,
                            income_account_id, expense_account_id, active)
                        VALUES (:id, 'INVENTORY', :name, :sku, :acc, :acc, true)
                        """)
                .param("id", UUID.randomUUID()).param("name", name).param("sku", sku)
                .param("acc", UUID.randomUUID()).update();
    }

    private UUID insertAccount(String name, String code) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO account (id, name, classification, type, detail_type,
                            code, active, postable)
                        VALUES (:id, :name, 'ASSET', 'BANK', 'CHECKING', :code, true, true)
                        """)
                .param("id", id).param("name", name).param("code", code).update();
        return id;
    }

    /** Мижоз тўлови (RCPT-) - alloc CHECK учун unallocated = total. */
    private void insertInvoicePayment(String number, UUID customerId, UUID accountId, String total) {
        jdbc.sql("""
                        INSERT INTO invoice_payment (id, receipt_number, customer_id,
                            payment_date, deposit_account_id, currency_id, exchange_rate,
                            total_amount, unallocated_amount, status)
                        VALUES (:id, :num, :cust, :date, :acc, :cur, 1, :total, :total, 'OPEN')
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("cust", customerId).param("date", LocalDate.of(2026, 7, 3))
                .param("acc", accountId).param("cur", uzsId)
                .param("total", new BigDecimal(total)).update();
    }

    /** Таъминотчи тўлови (PAY-) - alloc CHECK учун unallocated = total. */
    private void insertBillPayment(String number, UUID vendorId, UUID accountId, String total) {
        jdbc.sql("""
                        INSERT INTO bill_payment (id, payment_number, vendor_id,
                            payment_date, bank_account_id, currency_id, exchange_rate,
                            total_amount, unallocated_amount, status)
                        VALUES (:id, :num, :vend, :date, :acc, :cur, 1, :total, :total, 'OPEN')
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("vend", vendorId).param("date", LocalDate.of(2026, 7, 3))
                .param("acc", accountId).param("cur", uzsId)
                .param("total", new BigDecimal(total)).update();
    }

    /** Банк транзакцияси (BT-) - ref_no билан (AUD-016 сценарийси). */
    private void insertBankTransaction(String number, UUID accountId, String refNo, String total) {
        jdbc.sql("""
                        INSERT INTO bank_transaction (id, txn_number, type, bank_account_id,
                            txn_date, currency_id, exchange_rate, total, total_base,
                            status, ref_no)
                        VALUES (:id, :num, 'EXPENSE', :acc, :date, :cur, 1, :total, :total,
                            'POSTED', :ref)
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("acc", accountId).param("date", LocalDate.of(2026, 7, 4))
                .param("cur", uzsId).param("total", new BigDecimal(total))
                .param("ref", refNo).update();
    }

    /** Landed cost тақсимоти (LC-) - контактсиз, home валютали ҳужжат. */
    private void insertLandedCost(String number, String total) {
        jdbc.sql("""
                        INSERT INTO landed_cost_allocation (id, allocation_number,
                            allocation_date, total_amount, status)
                        VALUES (:id, :num, :date, :total, 'POSTED')
                        """)
                .param("id", UUID.randomUUID()).param("num", number)
                .param("date", LocalDate.of(2026, 7, 5))
                .param("total", new BigDecimal(total)).update();
    }
}
