package com.averpo.erp.shared;

import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.PostingException;
import com.averpo.erp.ledger.service.ProfitAndLossByClassService;
import com.averpo.erp.ledger.service.ProfitAndLossService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.sales.service.InvoiceService.LineData;
import com.averpo.erp.shared.domain.TxnClass;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.TxnClassService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Class tracking тестлари (docs/modules/class-tracking.md «Тестлар»
 * 1-5 бандлар): сатр class'ининг GL'га кўчиши, назорат/техник сатрлар
 * class'сизлиги, BR-CLS-001/002/003, P&amp;L by Class инварианти.
 * PER_TXN/OFF форма хулқи (6-7) - ClassTrackingWebTest'да.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassTrackingTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired TxnClassService txnClassService;
    @Autowired InvoiceService invoiceService;
    @Autowired BillService billService;
    @Autowired BankTransactionService bankService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired ProfitAndLossService plService;
    @Autowired ProfitAndLossByClassService plByClassService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;

    private TxnClass filial;
    private TxnClass online;
    private Contact customer;
    private Contact vendor;
    private Item service;
    private UUID rent;
    private UUID bank;
    private UUID usdBank;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        filial = txnClassService.create("Филиал А", null);
        online = txnClassService.create("Онлайн", filial.getId());
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Class мижози", null, null, null, null, null,
                null, null, null, null, null));
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Class етказувчиси", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts svc = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Class хизмати", null, null, null, null, null,
                svc.income(), null, null, svc.expense(), null, null));
        rent = accountRepository.findByName("Ижара").orElseThrow().getId();
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        usdBank = accountRepository.findByName("Валюта ҳисобварағи (USD)").orElseThrow().getId();
    }

    /** Манба ҳужжатнинг фаол GL ёзуви. */
    private JournalEntry glEntry(String sourceModule, UUID docId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                sourceModule, docId).orElseThrow();
    }

    /** Detail type бўйича биринчи сатр. */
    private JournalEntryLine lineOf(JournalEntry entry, String detailType) {
        return entry.getLines().stream()
                .filter(l -> l.getAccount().getDetailType().name().equals(detailType))
                .findFirst().orElseThrow();
    }

    /** Class'ли service сатри билан invoice post қилади. */
    private Invoice postInvoice(UUID classId, String price) {
        InvoiceData data = new InvoiceData(customer.getId(), DATE, null,
                null, null, null, false, List.of(new LineData(
                        service.getId(), null, BigDecimal.ONE, new BigDecimal(price),
                        null, null, null, null, null, null, classId)));
        return invoiceService.post(invoiceService.createDraft(data).getId());
    }

    /** Spec 1-банд: Invoice/Bill сатр class'и ўз легига кўчади, AR/AP class'сиз. */
    @Test
    void documentLineClass_flowsToOwnLeg_controlLegsClassless() {
        Invoice invoice = postInvoice(filial.getId(), "1000");
        JournalEntry invoiceEntry = glEntry(InvoiceService.SOURCE_MODULE, invoice.getId());
        assertThat(lineOf(invoiceEntry, "SERVICE_FEE_INCOME").getClassId())
                .isEqualTo(filial.getId());
        assertThat(lineOf(invoiceEntry, "ACCOUNTS_RECEIVABLE").getClassId()).isNull();

        Bill bill = billService.post(billService.createDraft(new BillService.BillData(
                vendor.getId(), "CLS-01", DATE, null, null, null, null, false,
                List.of(new BillService.LineData(BillLineType.EXPENSE, null, null,
                        null, null, rent, new BigDecimal("500"), null,
                        null, null, null, null, null, online.getId())))).getId());
        JournalEntry billEntry = glEntry(BillService.SOURCE_MODULE, bill.getId());
        assertThat(lineOf(billEntry, "RENT_OR_LEASE_OF_BUILDINGS").getClassId())
                .isEqualTo(online.getId());
        assertThat(lineOf(billEntry, "ACCOUNTS_PAYABLE").getClassId()).isNull();
    }

    /** Spec 2-банд: чет валюта ҳужжатида техник/жамланган сатрлар class'сиз. */
    @Test
    void foreignCurrency_technicalLegsClassless_balanced() {
        // Чет валюта invoice: даромад леглари class'ли, AR class'сиз.
        // Arbitr-087: чет валюта ҳужжати USD валютали контактга ёзилади
        Contact usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Class USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        InvoiceData data = new InvoiceData(usdCustomer.getId(), DATE, null,
                "USD", new BigDecimal("12600"), null, false, List.of(
                new LineData(service.getId(), null, BigDecimal.ONE,
                        new BigDecimal("100"), null, null, null, null, null, null,
                        filial.getId())));
        Invoice invoice = invoiceService.post(invoiceService.createDraft(data).getId());
        JournalEntry entry = glEntry(InvoiceService.SOURCE_MODULE, invoice.getId());
        assertThat(lineOf(entry, "SERVICE_FEE_INCOME").getClassId())
                .isEqualTo(filial.getId());
        assertThat(lineOf(entry, "ACCOUNTS_RECEIVABLE").getClassId()).isNull();
        assertBalanced(entry); // ТЕМИР ҚОИДА №7 - class суммага таъсир қилмади

        // Transfer конверсияси: FX (EXCHANGE_GAIN_OR_LOSS) сатри class'сиз
        var txn = bankService.transfer(new TransferData(bank, usdBank, DATE,
                new BigDecimal("1270000"), null,
                new BigDecimal("100"), new BigDecimal("12600"), null));
        JournalEntry transferEntry = glEntry(BankTransactionService.SOURCE_MODULE,
                txn.getId());
        assertThat(lineOf(transferEntry, "EXCHANGE_GAIN_OR_LOSS").getClassId()).isNull();
    }

    /** Home'да дебет == кредит (class билан/class'сиз бир хил инвариант). */
    private void assertBalanced(JournalEntry entry) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) debit = debit.add(line.getDebit().getBaseAmount());
            if (line.getCredit() != null) credit = credit.add(line.getCredit().getBaseAmount());
        }
        assertThat(debit).isEqualByComparingTo(credit);
    }

    /** Spec 3-банд: деактив class'ли сатр рад (draft'да ҳам, post'да ҳам). */
    @Test
    void inactiveClass_rejectedBrCls001() {
        // Draft'дан кейин нофаол қилинган class post'да тутилади
        Invoice draft = invoiceService.createDraft(new InvoiceData(customer.getId(),
                DATE, null, null, null, null, false, List.of(new LineData(
                        service.getId(), null, BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null, null, online.getId()))));
        txnClassService.setActive(online.getId(), false);
        assertThatThrownBy(() -> invoiceService.post(draft.getId()))
                .isInstanceOf(PostingException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-001"));

        // Янги ҳужжат нофаол class билан умуман яратилмайди
        assertThatThrownBy(() -> postInvoice(online.getId(), "100"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-001"));
    }

    /** Spec 4-банд: каталог қоидалари - дубликат ном (002) ва цикл (003). */
    @Test
    void catalogRules_duplicateAndCycle() {
        // BR-CLS-002: top-level'да ҳам, ота ичида ҳам ном ноёб
        assertThatThrownBy(() -> txnClassService.create("Филиал А", null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-002"));
        assertThatThrownBy(() -> txnClassService.create("Онлайн", filial.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-002"));
        // rename ҳам худди шу қоидада
        TxnClass dokon = txnClassService.create("Дўкон", filial.getId());
        assertThatThrownBy(() -> txnClassService.rename(dokon.getId(), "Онлайн"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-002"));

        // BR-CLS-003: ўзи ёки ўз авлоди ота бўлолмайди
        assertThatThrownBy(() -> txnClassService.changeParent(filial.getId(), filial.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-003"));
        assertThatThrownBy(() -> txnClassService.changeParent(filial.getId(), online.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CLS-003"));

        // Тўлиқ ном «Ота:Бола» (QBO FullyQualifiedName)
        assertThat(online.fullyQualifiedName()).isEqualTo("Филиал А:Онлайн");
    }

    /** Spec 5-банд (АСОСИЙ): P&L by Class устунлари йиғиндиси == оддий P&L. */
    @Test
    void plByClass_columnsSumExactlyToPlainPl() {
        postInvoice(filial.getId(), "1000");
        postInvoice(online.getId(), "700");
        postInvoice(null, "300"); // class'сиз - «Кўрсатилмаган» устуни
        // Харажат ҳам аралашсин (банк чиқими, class'ли сатр)
        bankService.expense(new BankTransactionService.TxnData(bank, DATE, null,
                null, null, List.of(new BankTransactionService.LineData(
                        rent, new BigDecimal("400"), null, null, filial.getId()))));

        ProfitAndLossService.Report plain = plService.build(DATE, DATE);
        ProfitAndLossByClassService.Report byClass = plByClassService.build(DATE, DATE);

        // Устунлар: Филиал А, Филиал А:Онлайн + Кўрсатилмаган (null)
        assertThat(byClass.columns()).hasSize(3);

        // Бўлим жамилари айнан тенг
        assertThat(byClass.income().total())
                .isEqualByComparingTo(plain.income().total());
        assertThat(byClass.expenses().total())
                .isEqualByComparingTo(plain.expenses().total());
        // Ҳар сатрда: устунлар йиғиндиси == сатр жамиси == оддий P&L сатри
        for (ProfitAndLossByClassService.Row row : byClass.income().rows()) {
            BigDecimal cellSum = row.cells().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(cellSum).isEqualByComparingTo(row.total());
            ProfitAndLossService.Row plainRow = plain.income().rows().stream()
                    .filter(r -> r.name().equals(row.name())).findFirst().orElseThrow();
            assertThat(row.total()).isEqualByComparingTo(plainRow.amount());
        }
        // Net Income: устунлар йиғиндиси ҳам, жами ҳам оддий P&L'га тенг
        BigDecimal netCellSum = byClass.netIncome().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netCellSum).isEqualByComparingTo(plain.netIncome());
        assertThat(byClass.netIncomeTotal()).isEqualByComparingTo(plain.netIncome());
    }

    /**
     * Arbitr-052 (045): flagship тестни кучайтириш - пул АНИҚ устунга
     * боғланиши (даромад 1000 filial / 700 online / 300 кўрсатилмаган
     * устунларида, per-cell) - аввалги тест фақат устунлар ЙИҒИНДИСИ ==
     * оддий P&L'ни текширарди (class аралашиб кетса ҳам ўтарди).
     */
    @Test
    void plByClass_amountsLandInExactColumn() {
        postInvoice(filial.getId(), "1000");
        postInvoice(online.getId(), "700");
        postInvoice(null, "300"); // «Кўрсатилмаган» устуни
        // Харажат ҳам аралашсин (class'ли банк чиқими) - «Кўрсатилмаган»
        // устуни шу постингдаги null-class легдан ҳосил бўлади, шусиз
        // устун сони 2 бўлиб қоларди (реал P&L манзарасини таъминлайди).
        bankService.expense(new BankTransactionService.TxnData(bank, DATE, null,
                null, null, List.of(new BankTransactionService.LineData(
                        rent, new BigDecimal("400"), null, null, filial.getId()))));

        ProfitAndLossByClassService.Report r = plByClassService.build(DATE, DATE);
        int fi = r.columns().indexOf(filial.fullyQualifiedName());
        int oi = r.columns().indexOf(online.fullyQualifiedName());
        assertThat(fi).isGreaterThanOrEqualTo(0);
        assertThat(oi).isGreaterThanOrEqualTo(0);
        int ni = 3 - fi - oi; // қолган индекс - «Кўрсатилмаган» устуни

        // Даромад пули АНИҚ устунга боғланди (финдингнинг ядроси - аввал
        // фақат устунлар йиғиндиси текширилар эди): filial 1000, online 700,
        // «Кўрсатилмаган» 300 - ҳар бири АЙНАН ўз устунида.
        assertThat(r.income().totals().get(fi)).isEqualByComparingTo("1000");
        assertThat(r.income().totals().get(oi)).isEqualByComparingTo("700");
        assertThat(r.income().totals().get(ni)).isEqualByComparingTo("300");
        // Даромад САТРи (Хизмат даромади) per-cell: filial ячейка 1000,
        // online 700, кўрсатилмаган 300 - пул айнан ўз устун ячейкасида
        // (аввалги тест фақат устун ЙИҒИНДИСИ == оддий P&L'ни текширарди).
        ProfitAndLossByClassService.Row incomeRow = r.income().rows().stream()
                .filter(row -> row.total().compareTo(new BigDecimal("2000")) == 0)
                .findFirst().orElseThrow();
        assertThat(incomeRow.cells().get(fi)).isEqualByComparingTo("1000");
        assertThat(incomeRow.cells().get(oi)).isEqualByComparingTo("700");
        assertThat(incomeRow.cells().get(ni)).isEqualByComparingTo("300");
    }
}
