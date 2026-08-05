package com.averpo.erp.ledger;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.StatementService;
import com.averpo.erp.ledger.service.StatementService.Row;
import com.averpo.erp.ledger.service.StatementService.Statement;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.CreditMemoService;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.sales.service.InvoicePaymentService.AllocationData;
import com.averpo.erp.sales.service.InvoicePaymentService.PaymentData;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StatementService тестлари (Statement 25-банд): мижоз кўчирмасининг
 * АСОСИЙ инварианти - давр боши қолдиқ + давр ҳаракатлари йиғиндиси ==
 * давр охири қолдиқ (invoice/тўлов/CM fixture билан) - ҳамда мижоз
 * фильтри тўғрилиги. Барча суммалар home валютада (base). GL'га
 * тегилмайди - фақат ўқиш.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatementServiceTest {

    /** Ҳаракат саналари: invoice < тўлов < credit memo. */
    private static final LocalDate D1 = LocalDate.of(2026, 7, 5);
    private static final LocalDate D2 = LocalDate.of(2026, 7, 10);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 15);

    @Autowired StatementService statementService;
    @Autowired InvoiceService invoiceService;
    @Autowired InvoicePaymentService paymentService;
    @Autowired CreditMemoService creditMemoService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired com.averpo.erp.ledger.service.AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired jakarta.persistence.EntityManager entityManager;

    /** Тест мижози. */
    private Contact customer;

    /** Хизмат item'и (омборсиз - AR ҳаракати учун етарли). */
    private Item service;

    /** Қабул банк счёти. */
    private UUID bankAccountId;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Кўчирма мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Кўчирма хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        bankAccountId = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
    }

    /** Мижозга home валютада POSTED invoice (Dr AR gross). */
    private Invoice postInvoice(UUID customerId, LocalDate date, String amount) {
        Invoice draft = invoiceService.createDraft(new InvoiceData(customerId, date,
                null, null, null, null, List.of(new InvoiceService.LineData(
                        service.getId(), null, BigDecimal.ONE, new BigDecimal(amount),
                        null, null))));
        return invoiceService.post(draft.getId());
    }

    /** Тўлов - invoice'га тўлиқ тақсимланган (Cr AR). */
    private void pay(LocalDate date, UUID invoiceId, String amount) {
        paymentService.create(new PaymentData(customer.getId(), date, bankAccountId,
                null, null, new BigDecimal(amount), null,
                List.of(new AllocationData(invoiceId, new BigDecimal(amount)))));
    }

    /** SERVICE сатрли credit memo (Cr AR gross). */
    private void creditMemo(LocalDate date, String amount) {
        creditMemoService.create(new CreditMemoService.CreditMemoData(customer.getId(),
                null, date, null, null, false, null,
                List.of(new CreditMemoService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal(amount), null, null, null, null, null))));
    }

    /**
     * Кўчирмани оладиган wrapper: raw SQL'дан ОЛДИН flush - тест
     * @Transactional ичида JPA ёзувлари (ҳужжат POSTED статуси)
     * JdbcClient'га кўринсин. StatementService raw SQL Hibernate
     * auto-flush'ни ишга туширмайди (у фақат JPQL'да); prod'да ҳужжат
     * бошқа tx'да commit бўлгани учун бу муаммо йўқ - соф тест артефакти.
     */
    private Statement stmt(UUID customerId, LocalDate from, LocalDate to) {
        entityManager.flush();
        return statementService.statement(customerId, from, to);
    }

    /** АСОСИЙ инвариант: опенинг + ҳаракатлар == клозинг; running balance тартиби. */
    @Test
    void invariant_openingPlusMovementsEqualsClosing() {
        Invoice inv = postInvoice(customer.getId(), D1, "100000");  // Dr AR +100 000
        pay(D2, inv.getId(), "40000");                 // Cr AR -40 000
        creditMemo(D3, "15000");                       // Cr AR -15 000

        Statement stmt = stmt(customer.getId(), D1, D3);

        // Давр бошида ҳаракат йўқ эди
        assertThat(stmt.opening()).isEqualByComparingTo("0");
        assertThat(stmt.rows()).hasSize(3);
        // Клозинг = 100 000 - 40 000 - 15 000
        assertThat(stmt.closing()).isEqualByComparingTo("45000");

        // ИНВАРИАНТ: опенинг + Σ(ҳаракат) == клозинг
        BigDecimal sum = stmt.rows().stream()
                .map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(stmt.opening().add(sum)).isEqualByComparingTo(stmt.closing());

        // Хронология ва running balance: invoice(+) → тўлов(-) → CM(-)
        assertThat(stmt.rows().get(0).amount()).isEqualByComparingTo("100000");
        assertThat(stmt.rows().get(0).balance()).isEqualByComparingTo("100000");
        assertThat(stmt.rows().get(0).sourceModule()).isEqualTo("INVOICE");
        assertThat(stmt.rows().get(0).documentNumber()).startsWith("INV-");

        assertThat(stmt.rows().get(1).amount()).isEqualByComparingTo("-40000");
        assertThat(stmt.rows().get(1).balance()).isEqualByComparingTo("60000");
        assertThat(stmt.rows().get(1).sourceModule()).isEqualTo("INVOICE_PAYMENT");
        assertThat(stmt.rows().get(1).documentNumber()).startsWith("RCPT-");

        assertThat(stmt.rows().get(2).amount()).isEqualByComparingTo("-15000");
        assertThat(stmt.rows().get(2).balance()).isEqualByComparingTo("45000");
        assertThat(stmt.rows().get(2).sourceModule()).isEqualTo("CREDIT_MEMO");
        assertThat(stmt.rows().get(2).documentNumber()).startsWith("CM-");
    }

    /** Давр ўртасидан бошласа: опенинг ундан олдинги ҳаракатларни жамлайди. */
    @Test
    void opening_midPeriod_reflectsPriorActivity() {
        Invoice inv = postInvoice(customer.getId(), D1, "100000");
        pay(D2, inv.getId(), "40000");
        creditMemo(D3, "15000");

        // D2'дан бошласак: invoice (D1) опенингга тушади
        Statement stmt = stmt(customer.getId(), D2, D3);
        assertThat(stmt.opening()).isEqualByComparingTo("100000");
        assertThat(stmt.rows()).hasSize(2);
        assertThat(stmt.rows().get(0).balance()).isEqualByComparingTo("60000");
        assertThat(stmt.rows().get(1).balance()).isEqualByComparingTo("45000");
        assertThat(stmt.closing()).isEqualByComparingTo("45000");
    }

    /** Мижоз фильтри: бошқа мижоз ҳаракатлари кўчирмага кирмайди. */
    @Test
    void customerFilter_excludesOtherCustomers() {
        postInvoice(customer.getId(), D1, "100000");
        Contact other = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Бошқа мижоз B", null, null, null, null, null,
                null, null, null, null, null));
        postInvoice(other.getId(), D1, "77777");

        Statement mine = stmt(customer.getId(), D1, D3);
        assertThat(mine.rows()).hasSize(1);
        assertThat(mine.closing()).isEqualByComparingTo("100000");

        Statement theirs = stmt(other.getId(), D1, D3);
        assertThat(theirs.rows()).hasSize(1);
        assertThat(theirs.closing()).isEqualByComparingTo("77777");
    }

    /** Ҳаракатсиз мижоз - бўш кўчирма (опенинг/клозинг нол). */
    @Test
    void noActivity_emptyStatement() {
        Contact fresh = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Ҳаракатсиз мижоз", null, null, null, null, null,
                null, null, null, null, null));
        Statement stmt = stmt(fresh.getId(), D1, D3);
        assertThat(stmt.opening()).isEqualByComparingTo("0");
        assertThat(stmt.rows()).isEmpty();
        assertThat(stmt.closing()).isEqualByComparingTo("0");
    }
}
