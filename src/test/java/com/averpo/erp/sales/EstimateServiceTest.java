package com.averpo.erp.sales;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Estimate;
import com.averpo.erp.sales.domain.EstimateStatus;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.EstimateService;
import com.averpo.erp.sales.service.EstimateService.EstimateData;
import com.averpo.erp.sales.service.EstimateService.LineData;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.web.InvoiceForm;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.tax.domain.TaxRate;
import com.averpo.erp.tax.service.TaxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Estimate тестлари (docs/modules/estimates-po.md «Тестлар» 1-3):
 * GL'сизлик (journal_entry сони ЎЗГАРМАЙДИ - асосий assert), status
 * оқимлари, айлантириш/linked ҳимоялари ва prefill мослиги.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EstimateServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired EstimateService estimateService;
    @Autowired InvoiceService invoiceService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired TaxRateService taxRateService;
    @Autowired JournalEntryRepository entryRepository;

    /** Тест мижози. */
    private Contact customer;

    /** Хизмат item'и (омборсиз - айлантириш drafts учун ҳам қулай). */
    private Item service;

    /** 12% тест ставкаси - snapshot/prefill текширувлари учун. */
    private TaxRate vat;

    /** Chart + мижоз + SERVICE item + ставка тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Estimate тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Лойиҳалаш хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        vat = taxRateService.create("VAT12E", "ҚҚС 12 (est)", new BigDecimal("12"));
    }

    /** Home валютадаги оддий estimate маълумоти. */
    private EstimateData data(List<LineData> lines) {
        return new EstimateData(customer.getId(), DATE, DATE.plusDays(30),
                null, null, "таклиф", false, lines);
    }

    /** Хизмат сатри ясагич (ихтиёрий ставка билан). */
    private LineData line(BigDecimal qty, BigDecimal price, TaxRate rate) {
        return new LineData(service.getId(), qty, price, null,
                rate == null ? null : rate.getId(), null);
    }

    /**
     * Arbitr-052 (043): BR-EST-001 валидация чегаралари - мижозсиз,
     * сатрсиз, миқдор 0/манфий, нарх манфий. (Финдинг: код тестда йўқ эди.)
     */
    /** Arbitr-087 (BR-EST-004): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // Бўш currency - server USD контактдан ўзи олади
        Contact usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Estimate USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        Estimate derived = estimateService.create(new EstimateData(usdCustomer.getId(),
                DATE, DATE.plusDays(30), null, new BigDecimal("12600"), "таклиф", false,
                List.of(line(BigDecimal.ONE, new BigDecimal("100"), null))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");

        // Клиент қиймати контактга (home) зид - BR-EST-004 рад
        assertThatThrownBy(() -> estimateService.create(new EstimateData(customer.getId(),
                DATE, DATE.plusDays(30), "USD", new BigDecimal("12600"), "x", false,
                List.of(line(BigDecimal.ONE, new BigDecimal("100"), null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-004"));
    }

    @Test
    void create_validationBoundaries_rejectedEst001() {
        // Мижозсиз
        assertEstRejected(new EstimateData(null, DATE, DATE.plusDays(30),
                null, null, "x", false,
                List.of(line(BigDecimal.ONE, new BigDecimal("100"), null))));
        // Камида битта сатр
        assertEstRejected(data(List.of()));
        // Миқдор 0 / манфий; нарх манфий
        assertEstRejected(data(List.of(line(BigDecimal.ZERO, new BigDecimal("100"), null))));
        assertEstRejected(data(List.of(line(new BigDecimal("-1"), new BigDecimal("100"), null))));
        assertEstRejected(data(List.of(line(BigDecimal.ONE, new BigDecimal("-1"), null))));
    }

    /** BR-EST-001 билан рад бўлишини тасдиқлайди (код айнан). */
    private void assertEstRejected(EstimateData data) {
        assertThatThrownBy(() -> estimateService.create(data))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-001"));
    }

    /**
     * Arbitr-052 (042): бир ХИЛ санали size+1 ҳужжат - LIST_SORT'даги id
     * tiebreaker туфайли 2 саҳифа қатор ўтказмайди/такрорламайди
     * (union = ҳаммаси, кесишма йўқ). createdAt ноёб эмаслиги яширинмайди.
     */
    @Test
    void list_sameDatePages_noOverlapOrSkip_idTiebreaker() {
        int total = EstimateService.LIST_PAGE_SIZE + 3;
        java.util.Set<java.util.UUID> created = new java.util.HashSet<>();
        for (int i = 0; i < total; i++) {
            created.add(estimateService.create(new EstimateData(customer.getId(),
                    DATE, DATE.plusDays(30), null, null, "таклиф", false,
                    List.of(line(BigDecimal.ONE, new BigDecimal("1000"), null)))).getId());
        }
        var page0 = estimateService.list(new EstimateService.ListFilter(null, null, null, null, null), 0);
        var page1 = estimateService.list(new EstimateService.ListFilter(null, null, null, null, null), 1);
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        page0.getContent().forEach(e -> seen.add(e.getId()));
        // Кесишма йўқ: page1'нинг ҳар қатори ЯНГИ (қатор такрорланмади)
        page1.getContent().forEach(e -> assertThat(seen.add(e.getId())).isTrue());
        assertThat(page0.getContent()).hasSize(EstimateService.LIST_PAGE_SIZE);
        assertThat(page1.getContent()).hasSize(total - EstimateService.LIST_PAGE_SIZE);
        // Бирлашма = яратилган ҳаммаси (қатор ўтказилмади)
        assertThat(seen).isEqualTo(created);
    }

    @Test
    void list_pagination_secondPageSlice_stableSort_statusFilter() {
        // Beruniy-perf1 2-босқич retrofit: size+1 estimate - 2-саҳифада
        // биттагина қолади; саналар ҳар хил - тартиб детерминистик
        Estimate oldest = null;
        Estimate newest = null;
        for (int i = EstimateService.LIST_PAGE_SIZE; i >= 0; i--) {
            Estimate est = estimateService.create(new EstimateData(customer.getId(),
                    DATE.minusDays(i), DATE.plusDays(30), null, null, "таклиф", false,
                    List.of(line(BigDecimal.ONE, new BigDecimal("1000"), null))));
            if (oldest == null) {
                oldest = est; // биринчи яратилгани энг эски санали
            }
            newest = est;
        }

        var page0 = estimateService.list(new EstimateService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(EstimateService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(EstimateService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = estimateService.list(new EstimateService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();

        // Статус филтри ҳам саҳифаланади: ҳаммаси PENDING - жами size+1
        assertThat(estimateService.list(new EstimateService.ListFilter(
                        null, null, EstimateStatus.PENDING, null, null), 0).getTotalElements())
                .isEqualTo(EstimateService.LIST_PAGE_SIZE + 1);
        // ACCEPTED ҳужжат йўқ - бўш саҳифа
        assertThat(estimateService.list(new EstimateService.ListFilter(
                null, null, EstimateStatus.ACCEPTED, null, null), 0).getTotalElements()).isZero();
    }

    /** Spec 1-банд: бутун ҳаёт цикли GL'га ҲЕЧ НАРСА ёзмайди. */
    @Test
    void lifecycle_createUpdateStatusDelete_journalEntryCountUnchanged() {
        long entriesBefore = entryRepository.count();

        Estimate estimate = estimateService.create(data(List.of(
                line(new BigDecimal("2"), new BigDecimal("5000"), vat),
                line(BigDecimal.ONE, new BigDecimal("3000"), null))));
        assertThat(estimate.getEstimateNumber()).startsWith("EST-2026-");
        assertThat(estimate.getStatus()).isEqualTo(EstimateStatus.PENDING);
        // ҚҚС фақат кўрсатишда: 10 000 net + 1 200 tax + 3 000 = 14 200 gross
        assertThat(estimate.getTotal()).isEqualByComparingTo("14200");
        assertThat(estimate.getLines().get(0).getTaxAmount()).isEqualByComparingTo("1200");
        assertThat(estimate.getLines().get(0).getTaxRateValue()).isEqualByComparingTo("12");

        estimateService.update(estimate.getId(), data(List.of(
                line(BigDecimal.ONE, new BigDecimal("7000"), null))));
        assertThat(estimateService.getWithLines(estimate.getId()).getTotal())
                .isEqualByComparingTo("7000");

        estimateService.changeStatus(estimate.getId(), EstimateStatus.ACCEPTED);
        estimateService.delete(estimate.getId());

        // АСОСИЙ ASSERT (spec): journal_entry сони ўзгармади - GL'сиз ҳужжат
        assertThat(entryRepository.count()).isEqualTo(entriesBefore);
    }

    /** Spec 2-банд: status оқимлари ва BR-EST-002 ҳимоялари. */
    @Test
    void statusFlow_pendingAcceptedClosed_rejectedGuards() {
        Estimate estimate = estimateService.create(data(List.of(
                line(BigDecimal.ONE, new BigDecimal("1000"), null))));

        // PENDING → ACCEPTED → CLOSED (қўлда)
        estimateService.changeStatus(estimate.getId(), EstimateStatus.ACCEPTED);
        assertThat(estimate.getStatus()).isEqualTo(EstimateStatus.ACCEPTED);
        estimateService.changeStatus(estimate.getId(), EstimateStatus.CLOSED);
        assertThat(estimate.getStatus()).isEqualTo(EstimateStatus.CLOSED);

        // CLOSED таҳрирланмайди (BR-EST-002)
        assertThatThrownBy(() -> estimateService.update(estimate.getId(),
                data(List.of(line(BigDecimal.ONE, new BigDecimal("2000"), null)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-002"));

        // Linked'сиз CLOSED → PENDING қайта очилади; ACCEPTED'га тўғри йўл тақиқ
        assertThatThrownBy(() -> estimateService.changeStatus(estimate.getId(),
                EstimateStatus.ACCEPTED))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-002"));
        estimateService.changeStatus(estimate.getId(), EstimateStatus.PENDING);
        assertThat(estimate.getStatus()).isEqualTo(EstimateStatus.PENDING);

        // REJECTED: айлантириш рад (BR-EST-002), тўғри ACCEPTED ҳам рад
        estimateService.changeStatus(estimate.getId(), EstimateStatus.REJECTED);
        assertThatThrownBy(() -> estimateService.requireConvertible(estimate.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-002"));
        assertThatThrownBy(() -> estimateService.changeStatus(estimate.getId(),
                EstimateStatus.ACCEPTED))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-002"));
    }

    /** Spec 3-банд: prefill мослиги, айлантириш белгиси, linked ҳимоялари. */
    @Test
    void convert_prefillMatches_marksClosedLinked_thenGuards() {
        Estimate estimate = estimateService.create(data(List.of(
                line(new BigDecimal("3"), new BigDecimal("4000"), vat))));

        // Prefill (InvoiceController оқимидаги хаританинг ўзи): мижоз/
        // валюта/сатрлар/ставка айнан кўчади, сана - бугунги (default)
        InvoiceForm form = InvoiceForm.fromEstimate(
                estimateService.requireConvertible(estimate.getId()), "UZS");
        assertThat(form.getEstimateId()).isEqualTo(estimate.getId().toString());
        assertThat(form.getCustomerId()).isEqualTo(customer.getId().toString());
        assertThat(form.getCurrency()).isEqualTo("UZS");
        assertThat(form.getLines()).hasSize(1);
        assertThat(form.getLines().get(0).getItemId()).isEqualTo(service.getId().toString());
        assertThat(form.getLines().get(0).getQuantity()).isEqualTo("3");
        assertThat(form.getLines().get(0).getUnitPrice()).isEqualTo("4000");
        assertThat(form.getLines().get(0).getTaxRateId()).isEqualTo(vat.getId().toString());

        // «Фойдаланувчи сақлади»: оддий invoice draft оқими, кейин mark
        Invoice invoice = invoiceService.createDraft(new InvoiceService.InvoiceData(
                customer.getId(), DATE, null, null, null, null,
                List.of(new InvoiceService.LineData(service.getId(), null,
                        new BigDecimal("3"), new BigDecimal("4000"), null, null,
                        null, vat.getId(), null, null))));
        estimateService.markConverted(estimate.getId(), invoice.getId());

        assertThat(estimate.getStatus()).isEqualTo(EstimateStatus.CLOSED);
        assertThat(estimate.getInvoiceId()).isEqualTo(invoice.getId());
        // Invoice кўришидаги «Estimate'дан» белгиси манбаи
        assertThat(estimateService.findByInvoiceId(invoice.getId()))
                .contains(estimate);

        // Linked ўчирилмайди ва қайта айлантирилмайди (BR-EST-003)
        assertThatThrownBy(() -> estimateService.delete(estimate.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-003"));
        assertThatThrownBy(() -> estimateService.markConverted(estimate.getId(),
                invoice.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-003"));
        // Linked CLOSED қайта очилмайди ҳам (BR-EST-002)
        assertThatThrownBy(() -> estimateService.changeStatus(estimate.getId(),
                EstimateStatus.PENDING))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-EST-002"));
    }
}
