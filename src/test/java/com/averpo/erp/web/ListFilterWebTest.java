package com.averpo.erp.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Рўйхат филтрлари web тестлари (DEC-068, list-filters.md «Тестлар»
 * 1-3): ҳар модулда давр+статус+матн филтри web орқали рўйхатни тўғри
 * кесиши, кирилл матн қидируви катта-кичик фарқсизлиги ва pagination
 * линклари филтрни сақлаши текширилади. Филтрсиз default регресси -
 * мавжуд {@link ScreenSmokeTest}'да (спец 4-банд).
 *
 * <p>Дата ҚЎЛДА JdbcClient билан қуйилади (GlobalSearchServiceTest
 * нақши) - филтр SQL'и хом жадвалда синалади, seed'га боғланмайди.
 * Ноёб кирилл {@code T} токени seed маълумотида учрамайди.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class ListFilterWebTest {

    /** Seed'да учрамайдиган ноёб кирилл токени (катта-кичик тести ҳам шунда). */
    private static final String T = "Жасфилтр";

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;

    /** Security filter chain уланган MockMvc (ScreenSmokeTest қолипи). */
    private MockMvc mockMvc;

    /** Ҳужжат FK'лари учун seed'даги home валюта (UZS) id'си. */
    private UUID uzsId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        uzsId = jdbc.sql("SELECT id FROM currency WHERE code = 'UZS'")
                .query(UUID.class).single();
    }

    /** Sales: invoice рўйхати - давр, статус ва кирилл матн филтрлари. */
    @Test
    void invoices_periodStatusAndCyrillicText() throws Exception {
        UUID customer = insertContact("CUSTOMER", T + " мижози", true);
        insertInvoice("INV-" + T + "-A", customer, LocalDate.of(2026, 7, 1),
                "POSTED", T + " Синов изоҳи");
        insertInvoice("INV-" + T + "-B", customer, LocalDate.of(2026, 6, 1),
                "DRAFT", null);

        // Давр: 2026-06-15 дан - фақат кейинги ҳужжат
        mockMvc.perform(get("/invoices").param("from", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INV-" + T + "-A")))
                .andExpect(content().string(not(containsString("INV-" + T + "-B"))));
        // Статус: DRAFT - фақат қоралама
        mockMvc.perform(get("/invoices").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INV-" + T + "-B")))
                .andExpect(content().string(not(containsString("INV-" + T + "-A"))));
        // Матн: изоҳ КИЧИК ҳарфда қидирилади - кирилл case-insensitive (спец 2)
        mockMvc.perform(get("/invoices").param("q", "жасфилтр синов"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INV-" + T + "-A")))
                .andExpect(content().string(not(containsString("INV-" + T + "-B"))));
    }

    /** Спец 3: pagination + филтр бирга - 2-саҳифа линклари филтрни сақлайди. */
    @Test
    void invoices_paginationKeepsFilter() throws Exception {
        UUID customer = insertContact("CUSTOMER", T + " саҳифа мижози", true);
        // 26 та POSTED - 25 лик саҳифадан ошади, 2-саҳифа пайдо бўлади
        for (int i = 1; i <= 26; i++) {
            insertInvoice(String.format("INV-%s-PG-%02d", T, i), customer,
                    LocalDate.of(2026, 5, 5), "POSTED", null);
        }
        mockMvc.perform(get("/invoices").param("status", "POSTED").param("page", "1"))
                .andExpect(status().isOk())
                // 2-саҳифада ҳам айнан шу филтр кесимидан ёзув бор. Қайсиси -
                // аҳамиятсиз: сана бир хил, created_at транзакцияда тенг,
                // id тасодифий UUID - тартиб башорат қилинмайди
                .andExpect(content().string(containsString("INV-" + T + "-PG-")))
                // Саҳифа линки филтрни сақлайди. DEC-105 (QBO pager) линкка
                // &size= қўшган; 105б HTML-валидлик тозалови билан статик
                // амперсанд ҳам «&amp;» бўлиб ёзилади - бутун линк изчил
                // «?page=0&amp;size=<N>&amp;status=POSTED». Интент ЎША -
                // филтр (status) саҳифа линкларида сақланиши; size
                // қийматига боғланмаймиз.
                .andExpect(content().string(containsString("?page=0&amp;size=")))
                .andExpect(content().string(containsString("&amp;status=POSTED")));
    }

    /** Purchase: bill рўйхати - давр, статус ва матн (vendor ҳисобварағи ҳам). */
    @Test
    void bills_periodStatusAndText() throws Exception {
        UUID vendor = insertContact("VENDOR", T + " таъминотчиси", true);
        insertBill("BILL-" + T + "-A", vendor, LocalDate.of(2026, 7, 2),
                "POSTED", T + " Харид изоҳи");
        insertBill("BILL-" + T + "-B", vendor, LocalDate.of(2026, 6, 2),
                "DRAFT", null);

        mockMvc.perform(get("/bills").param("from", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BILL-" + T + "-A")))
                .andExpect(content().string(not(containsString("BILL-" + T + "-B"))));
        mockMvc.perform(get("/bills").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BILL-" + T + "-B")))
                .andExpect(content().string(not(containsString("BILL-" + T + "-A"))));
        mockMvc.perform(get("/bills").param("q", "жасфилтр харид"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BILL-" + T + "-A")))
                .andExpect(content().string(not(containsString("BILL-" + T + "-B"))));
    }

    /** Bank: txn рўйхати - давр, статус ва ref_no бўйича матн қидируви. */
    @Test
    void bankTransactions_periodStatusAndRefNo() throws Exception {
        UUID bank = insertAccount(T + " банк счёти");
        insertBankTxn("BT-" + T + "-A", bank, LocalDate.of(2026, 7, 3),
                "POSTED", "РЕФ" + T + "9", null);
        insertBankTxn("BT-" + T + "-B", bank, LocalDate.of(2026, 6, 3),
                "REVERSED", null, null);

        mockMvc.perform(get("/bank-transactions").param("from", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BT-" + T + "-A")))
                .andExpect(content().string(not(containsString("BT-" + T + "-B"))));
        mockMvc.perform(get("/bank-transactions").param("status", "REVERSED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BT-" + T + "-B")))
                .andExpect(content().string(not(containsString("BT-" + T + "-A"))));
        // ref_no бўйича, кичик ҳарфда (кирилл fold) - AUD-016/068 изчиллиги
        mockMvc.perform(get("/bank-transactions").param("q", "реф" + T.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BT-" + T + "-A")))
                .andExpect(content().string(not(containsString("BT-" + T + "-B"))));
    }

    /** Ledger: JE рўйхати - давр, статус ва тавсиф бўйича матн. */
    @Test
    void journalEntries_periodStatusAndText() throws Exception {
        insertJournalEntry("JE-" + T + "-A", LocalDate.of(2026, 7, 4),
                "POSTED", T + " Проводка тавсифи");
        insertJournalEntry("JE-" + T + "-B", LocalDate.of(2026, 6, 4),
                "DRAFT", null);

        mockMvc.perform(get("/journal-entries")
                        .param("from", "2026-06-15").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JE-" + T + "-A")))
                .andExpect(content().string(not(containsString("JE-" + T + "-B"))));
        mockMvc.perform(get("/journal-entries")
                        .param("from", "2026-01-01").param("to", "2026-12-31")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JE-" + T + "-B")))
                .andExpect(content().string(not(containsString("JE-" + T + "-A"))));
        mockMvc.perform(get("/journal-entries")
                        .param("from", "2026-01-01").param("to", "2026-12-31")
                        .param("q", "жасфилтр проводка"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JE-" + T + "-A")))
                .andExpect(content().string(not(containsString("JE-" + T + "-B"))));
    }

    /** Каталог: контактлар - фаоллик (default фаол) ва кирилл матн. */
    @Test
    void contacts_activityAndCyrillicText() throws Exception {
        insertContact("CUSTOMER", T + "Фаол", true);
        insertContact("CUSTOMER", T + "Ётган", false);

        // Default - фақат фаоллар (спец: каталог default'и фаол)
        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(T + "Фаол")))
                .andExpect(content().string(not(containsString(T + "Ётган"))));
        // Нофаоллар кесими
        mockMvc.perform(get("/customers").param("activity", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(T + "Ётган")))
                .andExpect(content().string(not(containsString(T + "Фаол"))));
        // Матн кичик ҳарфда - кирилл case-insensitive
        mockMvc.perform(get("/customers").param("q", T.toLowerCase() + "фаол"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(T + "Фаол")));
    }

    /** Inventory каталоги: омборлар - матн ва фаоллик (кодлар ноёб белги). */
    @Test
    void warehouses_textAndActivity() throws Exception {
        insertWarehouse(T + " омбори", "ЖФЛТ1", true);
        insertWarehouse(T + " ёпиқ омбори", "ЖФЛТ2", false);

        // Матн кичик ҳарфда - фақат мос ном (кодлар билан текширилади)
        mockMvc.perform(get("/settings/warehouses").param("q", T.toLowerCase() + " омбори"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ЖФЛТ1")))
                .andExpect(content().string(not(containsString("ЖФЛТ2"))));
        mockMvc.perform(get("/settings/warehouses").param("activity", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ЖФЛТ2")))
                .andExpect(content().string(not(containsString("ЖФЛТ1"))));
    }

    /**
     * Inventory: қолдиқлар - филтр + саҳифа БИРГА (DEC-105б D):
     * item қидируви кесимида 2-саҳифа очилади, филтр (q) саҳифа
     * линкларида сақланади. Ном тартиби zero-padded суффикс билан
     * детерминик - қайси саҳифада қайси ном чиқиши башоратли.
     */
    @Test
    void inventoryBalances_filterAndPageTogether() throws Exception {
        UUID warehouse = insertWarehouse(T + " қолдиқ омбори", "ЖФЛТ3", true);
        UUID account = insertAccount(T + " қолдиқ счёти");
        // 26 та item + нол бўлмаган қолдиқ - 25 лик саҳифадан ошади
        for (int i = 1; i <= 26; i++) {
            UUID item = insertItem(String.format("%sБаланс-%02d", T, i), account);
            insertStockBalance(warehouse, item);
        }

        // 1-саҳифа: биринчи 25 ном; page линки филтрни (q) сақлайди
        mockMvc.perform(get("/inventory/balances").param("q", (T + "баланс").toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(T + "Баланс-01")))
                .andExpect(content().string(not(containsString(T + "Баланс-26"))))
                .andExpect(content().string(containsString("?page=1&amp;size=")))
                .andExpect(content().string(containsString("&amp;q=")));
        // 2-саҳифа ўша филтр билан: фақат 26-ном қолади
        mockMvc.perform(get("/inventory/balances")
                        .param("q", (T + "баланс").toLowerCase()).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(T + "Баланс-26")))
                .andExpect(content().string(not(containsString(T + "Баланс-01"))));
    }

    /**
     * DEC-105б устун саралаш (invoice): сана бўйича asc/desc тартиб,
     * жорий устунда стрелка, th линки филтрни сақлайди, саҳифа линклари
     * sort'ни сақлайди, нотаниш калит жим default'га тушади (whitelist).
     */
    @Test
    void invoices_columnSorting() throws Exception {
        UUID customer = insertContact("CUSTOMER", T + " сорт мижози", true);
        insertInvoice("INV-" + T + "-СОРТ-А", customer, LocalDate.of(2026, 3, 1),
                "POSTED", null);
        insertInvoice("INV-" + T + "-СОРТ-Б", customer, LocalDate.of(2026, 4, 1),
                "POSTED", null);

        // Default: сана desc - Б (янги) олдин А
        String byDefault = mockMvc.perform(get("/invoices").param("q", T.toLowerCase()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(byDefault.indexOf("-СОРТ-Б")).isLessThan(byDefault.indexOf("-СОРТ-А"));

        // sort=date&dir=asc: А (эски) олдин Б; стрелка ↑; линклар тўғри
        String asc = mockMvc.perform(get("/invoices").param("q", T.toLowerCase())
                        .param("sort", "date").param("dir", "asc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(asc.indexOf("-СОРТ-А")).isLessThan(asc.indexOf("-СОРТ-Б"));
        assertThat(asc).contains("↑");
        // Жорий устун th линки кейинги босишда тескарисига олади ва филтрни сақлайди
        assertThat(asc).contains("?sort=date&amp;dir=desc&amp;q=");
        // Саҳифа линклари sort'ни сақлайди (pageQuery = филтр + sort)
        assertThat(asc).contains("&amp;sort=date&amp;dir=asc");

        // dir=desc: Б олдин А; стрелка ↓
        String desc = mockMvc.perform(get("/invoices").param("q", T.toLowerCase())
                        .param("sort", "date").param("dir", "desc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(desc.indexOf("-СОРТ-Б")).isLessThan(desc.indexOf("-СОРТ-А"));
        assertThat(desc).contains("↓");

        // Нотаниш калит (whitelist'дан ташқари) - жим default, хато йўқ
        mockMvc.perform(get("/invoices").param("sort", "ёмонкалит").param("dir", "asc"))
                .andExpect(status().isOk());
    }

    /** DEC-105б устун саралаш (bill): рақам бўйича asc/desc. */
    @Test
    void bills_columnSorting() throws Exception {
        UUID vendor = insertContact("VENDOR", T + " сорт таъминотчиси", true);
        insertBill("BILL-" + T + "-СОРТ-А", vendor, LocalDate.of(2026, 7, 2),
                "POSTED", null);
        insertBill("BILL-" + T + "-СОРТ-Б", vendor, LocalDate.of(2026, 7, 2),
                "POSTED", null);

        String asc = mockMvc.perform(get("/bills").param("q", T.toLowerCase())
                        .param("sort", "number").param("dir", "asc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(asc.indexOf("-СОРТ-А")).isLessThan(asc.indexOf("-СОРТ-Б"));
        assertThat(asc).contains("↑");

        String desc = mockMvc.perform(get("/bills").param("q", T.toLowerCase())
                        .param("sort", "number").param("dir", "desc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(desc.indexOf("-СОРТ-Б")).isLessThan(desc.indexOf("-СОРТ-А"));
        assertThat(desc).contains("↓");
    }

    /** DEC-105б устун саралаш (JE): рақам бўйича asc/desc (107 ёпилган зона). */
    @Test
    void journalEntries_columnSorting() throws Exception {
        insertJournalEntry("JE-" + T + "-СОРТ-А", LocalDate.of(2026, 7, 4),
                "POSTED", null);
        insertJournalEntry("JE-" + T + "-СОРТ-Б", LocalDate.of(2026, 7, 4),
                "POSTED", null);

        String asc = mockMvc.perform(get("/journal-entries").param("q", T.toLowerCase())
                        .param("sort", "number").param("dir", "asc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(asc.indexOf("-СОРТ-А")).isLessThan(asc.indexOf("-СОРТ-Б"));
        assertThat(asc).contains("↑");

        String desc = mockMvc.perform(get("/journal-entries").param("q", T.toLowerCase())
                        .param("sort", "number").param("dir", "desc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(desc.indexOf("-СОРТ-Б")).isLessThan(desc.indexOf("-СОРТ-А"));
        assertThat(desc).contains("↓");
    }

    /** Payroll: run рўйхати - давр, статус ва матн. */
    @Test
    void payrollRuns_periodStatusAndText() throws Exception {
        insertPayrollRun("PAYR-" + T + "-A", "2098-01", LocalDate.of(2026, 7, 5),
                "POSTED", T + " Ойлик изоҳи");
        insertPayrollRun("PAYR-" + T + "-B", "2098-02", LocalDate.of(2026, 6, 5),
                "DRAFT", null);

        mockMvc.perform(get("/payroll").param("from", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PAYR-" + T + "-A")))
                .andExpect(content().string(not(containsString("PAYR-" + T + "-B"))));
        mockMvc.perform(get("/payroll").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PAYR-" + T + "-B")))
                .andExpect(content().string(not(containsString("PAYR-" + T + "-A"))));
        mockMvc.perform(get("/payroll").param("q", "жасфилтр ойлик"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PAYR-" + T + "-A")))
                .andExpect(content().string(not(containsString("PAYR-" + T + "-B"))));
    }

    // ---- Дата ёрдамчилари (хом JdbcClient insert - GlobalSearchServiceTest нақши) ----

    /** Минимал контакт (фаоллиги билан) - FK ва каталог тестлари учун. */
    private UUID insertContact(String type, String displayName, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO contact (id, type, display_name, active) VALUES (?, ?, ?, ?)")
                .params(id, type, displayName, active).update();
        return id;
    }

    /** Минимал invoice - филтр майдонлари билан. */
    private void insertInvoice(String number, UUID customerId, LocalDate date,
                               String status, String memo) {
        jdbc.sql("INSERT INTO invoice (id, invoice_number, customer_id, invoice_date, "
                        + "currency_id, exchange_rate, status, total, total_base, memo) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?, 100, 100, ?)")
                .params(UUID.randomUUID(), number, customerId, date, uzsId, status, memo)
                .update();
    }

    /** Минимал bill - филтр майдонлари билан. */
    private void insertBill(String number, UUID vendorId, LocalDate date,
                            String status, String memo) {
        jdbc.sql("INSERT INTO bill (id, bill_number, vendor_id, bill_date, "
                        + "currency_id, exchange_rate, status, total, total_base, memo) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?, 100, 100, ?)")
                .params(UUID.randomUUID(), number, vendorId, date, uzsId, status, memo)
                .update();
    }

    /** Минимал банк счёти (bank txn FK'си учун). */
    private UUID insertAccount(String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO account (id, name, classification, type, detail_type, "
                        + "active, postable) "
                        + "VALUES (?, ?, 'ASSET', 'BANK', 'CHECKING', true, true)")
                .params(id, name).update();
        return id;
    }

    /** Минимал банк транзакцияси - ref_no қидируви учун. */
    private void insertBankTxn(String number, UUID accountId, LocalDate date,
                               String status, String refNo, String memo) {
        jdbc.sql("INSERT INTO bank_transaction (id, txn_number, type, bank_account_id, "
                        + "txn_date, currency_id, exchange_rate, total, total_base, "
                        + "status, ref_no, memo) "
                        + "VALUES (?, ?, 'EXPENSE', ?, ?, ?, 1, 100, 100, ?, ?, ?)")
                .params(UUID.randomUUID(), number, accountId, date, uzsId,
                        status, refNo, memo)
                .update();
    }

    /** Минимал GL ёзуви - тавсиф қидируви учун. */
    private void insertJournalEntry(String number, LocalDate date,
                                    String status, String description) {
        jdbc.sql("INSERT INTO journal_entry (id, entry_number, entry_date, status, "
                        + "description) VALUES (?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), number, date, status, description).update();
    }

    /** Минимал омбор - каталог филтри ва қолдиқ тестлари учун. */
    private UUID insertWarehouse(String name, String code, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO warehouse (id, name, code, active) VALUES (?, ?, ?, ?)")
                .params(id, name, code, active).update();
        return id;
    }

    /** Минимал SERVICE item - қолдиқ филтри учун (счёт id'лари хом UUID устун). */
    private UUID insertItem(String name, UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO item (id, type, name, income_account_id, "
                        + "expense_account_id, active) "
                        + "VALUES (?, 'SERVICE', ?, ?, ?, true)")
                .params(id, name, accountId, accountId).update();
        return id;
    }

    /** Нол бўлмаган қолдиқ ёзуви (hideZero default филтридан ўтади). */
    private void insertStockBalance(UUID warehouseId, UUID itemId) {
        jdbc.sql("INSERT INTO stock_balance (id, warehouse_id, item_id, qty, avg_cost) "
                        + "VALUES (?, ?, ?, 5, 100)")
                .params(UUID.randomUUID(), warehouseId, itemId).update();
    }

    /** Минимал payroll run - period partial unique'ига тегмас узоқ ойлар. */
    private void insertPayrollRun(String number, String period, LocalDate date,
                                  String status, String memo) {
        jdbc.sql("INSERT INTO payroll_run (id, run_number, period, run_date, status, memo) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), number, period, date, status, memo).update();
    }
}
