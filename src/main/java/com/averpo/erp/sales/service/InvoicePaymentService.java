package com.averpo.erp.sales.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoicePayment;
import com.averpo.erp.sales.domain.InvoicePaymentAllocation;
import com.averpo.erp.sales.domain.InvoiceStatus;
import com.averpo.erp.sales.repo.InvoicePaymentAllocationRepository;
import com.averpo.erp.sales.repo.InvoicePaymentRepository;
import com.averpo.erp.sales.repo.InvoiceRepository;
import com.averpo.erp.shared.Fx;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Мижоз тўлови (тушум)нинг ягона public API'си (docs/modules/sales.md)
 * - BillPaymentService'нинг кўзгу акси. DRAFT йўқ: яратилди = POSTED
 * (QBO услуби), тузатиш reverse орқали. Аванс рухсат - тақсимланмаган
 * қисм AR'да мижоз аванси (кредит қолдиқ) бўлиб туради, кейин
 * {@link #allocate} билан ишлатилади.
 *
 * <p>GL - фақат PostingService (ТЕМИР ҚОИДА №2). Realized курс фарқи
 * ҳар allocation учун АЛОҲИДА JE; ЙЎНАЛИШИ AP томонга ТЕСКАРИ: фарқ
 * base = allocation × (тўлов курси - invoice курси), мусбат - фойда
 * (AR Dt / gain Cr) - posting-rules «Сотув».
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InvoicePaymentService {

    /** Тушум GL ёзувининг манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "INVOICE_PAYMENT";

    /** Курс фарқи JE'ларининг манба модул белгиси - docId = allocation id. */
    public static final String ALLOCATION_SOURCE_MODULE = "RECEIPT_ALLOCATION";

    /** Тушум формаси маълумотлари - тақсимот бирга келиши ҳам мумкин. */
    public record PaymentData(UUID customerId, LocalDate paymentDate,
                              UUID depositAccountId, String currency,
                              BigDecimal exchangeRate, BigDecimal totalAmount,
                              String memo, List<AllocationData> allocations) { }

    /** Битта тақсимот: қайси invoice'га қанча (тўлов валютасида). */
    public record AllocationData(UUID invoiceId, BigDecimal amount) { }

    /** Тушум репозиторийси. */
    private final InvoicePaymentRepository repository;

    /** Тақсимот репозиторийси. */
    private final InvoicePaymentAllocationRepository allocationRepository;

    /** Invoice қолдиқ/денормализацияси учун - ўз модулимиз ичида. */
    private final InvoiceRepository invoiceRepository;

    /** Ҳужжат рақамлари (RCPT-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Customer текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Қабул счёти валидацияси ва тизим счётлари (AR, курс фарқи). */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public InvoicePayment get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тушум топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 2-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экран тартиби
     * ўзгармасин (Beruniy-perf1, BillPaymentService кўзгуси).
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("paymentDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Рўйхат филтри (Arbitr-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             InvoicePayment.Status status, UUID customerId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (Beruniy-perf1), тўлиқ филтр
     * (Arbitr-068): давр/статус/мижоз/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InvoicePayment> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("paymentDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("paymentDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("customerId", filter.customerId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "receiptNumber", "memo")),
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InvoicePayment> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Тўловнинг тақсимотлари - кўриш экрани ва тестлар учун. */
    @Transactional(readOnly = true)
    public List<InvoicePaymentAllocation> allocationsOf(UUID paymentId) {
        return allocationRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    /** Invoice'га кетган тақсимотлар - invoice кўриш экрани учун. */
    @Transactional(readOnly = true)
    public List<InvoicePaymentAllocation> allocationsForInvoice(UUID invoiceId) {
        return allocationRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    /**
     * Тушум яратади - дарҳол POSTED: GL (қабул счёти Dt / AR Cr тўлиқ
     * суммага, аванс қисми ҳам шу ёзув ичида) + берилган тақсимотлар.
     * Давр қулфи (BR-LED-020) ва idempotency PostingService'дан.
     *
     * @throws BusinessRuleException BR-RCPT-001..006, 008..012
     */
    public InvoicePayment create(PaymentData data) {
        Normalized normalized = validate(data);
        InvoicePayment payment = new InvoicePayment(
                sequenceService.next(DocumentType.RECEIPT, data.paymentDate()),
                data.customerId(), data.paymentDate(), data.depositAccountId(),
                normalized.currency(), normalized.rate(), data.totalAmount(),
                Strings.blankToNull(data.memo()));
        repository.saveAndFlush(payment);

        Money amount = money(payment, payment.getTotalAmount());
        postingService.createAndPost(new JournalEntryRequest(
                payment.getPaymentDate(),
                "Тушум " + payment.getReceiptNumber() + " - "
                        + customerName(payment.getCustomerId()),
                SOURCE_MODULE, payment.getId(), List.of(
                        new JournalEntryRequest.Line(
                                payment.getDepositAccountId(),
                                amount, null, payment.getCustomerId(), null, null, null),
                        new JournalEntryRequest.Line(
                                accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_RECEIVABLE),
                                null, amount, payment.getCustomerId(), null, null, null))));

        if (data.allocations() != null && !data.allocations().isEmpty()) {
            applyAllocations(payment, data.allocations());
        }
        return payment;
    }

    /**
     * Мавжуд POSTED тушумдан кейинги тақсимот (аванс ишлатиш) - тушум
     * билан бирга келган тақсимот билан айнан бир хил йўл (курс фарқи
     * ҳам худди шундай ҳисобланади).
     *
     * @throws BusinessRuleException BR-RCPT-003..006, 009, 011, 013
     */
    public InvoicePayment allocate(UUID paymentId, List<AllocationData> allocations) {
        InvoicePayment payment = get(paymentId);
        if (payment.getStatus() != InvoicePayment.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_013,
                    "Allocation фақат POSTED тушумдан: " + payment.getReceiptNumber()
                    + " ҳозир " + payment.getStatus());
        }
        applyAllocations(payment, allocations);
        return payment;
    }

    /**
     * Reverse: тақсимотларнинг курс фарқи JE'лари сторно + invoice
     * денормализациялари қайтарилади + тушумнинг ўз GL ёзуви сторно.
     * Тақсимот ёзувлари аудит учун ўчирилмайди - тўлов REVERSED
     * бўлгани уларни бекор қилади.
     *
     * @throws BusinessRuleException BR-RCPT-007
     */
    public InvoicePayment reverse(UUID id, LocalDate reversalDate, String reason) {
        InvoicePayment payment = get(id);
        if (payment.getStatus() != InvoicePayment.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_007,
                    "Фақат POSTED тушум reverse қилинади: " + payment.getReceiptNumber()
                    + " ҳозир " + payment.getStatus());
        }
        String storno = reason == null || reason.isBlank() ? "Тушум reverse" : reason;
        for (InvoicePaymentAllocation allocation : allocationsOf(payment.getId())) {
            Invoice invoice = allocation.getInvoice();
            // Нол фарқда JE ёзилмаган - фарқни қайта ҳисоблаб аниқлаймиз
            // (формула allocation пайтидагиси билан бир хил, детерминистик)
            if (Fx.realizedFxDifference(allocation.getAmount(), payment.getExchangeRate(), invoice.getExchangeRate()).signum() != 0) {
                postingService.reverseBySource(ALLOCATION_SOURCE_MODULE,
                        allocation.getId(), reversalDate, storno);
            }
            invoice.applyPaidAmount(invoice.getPaidAmount().subtract(allocation.getAmount()));
        }
        postingService.reverseBySource(SOURCE_MODULE, payment.getId(),
                reversalDate, storno);
        // Тўловнинг ўз денормализацияси ҳам тикланади (Beruniy-008,
        // BillPayment'даги Beruniy-002 тузатишининг кўзгуси) - акс
        // ҳолда REVERSED тушумда allocated эски қийматда қолар эди
        payment.applyAllocated(BigDecimal.ZERO);
        payment.markReversed();
        return payment;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate) { }

    /** Тушум сарлавҳаси валидацияси (BR-RCPT-001/002/008/010/012). */
    private Normalized validate(PaymentData data) {
        if (data.customerId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_010,
                    "Customer танланиши шарт");
        }
        Contact customer = contactService.get(data.customerId());
        if (customer.getType() != ContactType.CUSTOMER || !customer.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_010,
                    "Customer фаол CUSTOMER типдаги контакт бўлиши шарт: "
                    + customer.getDisplayName());
        }
        if (data.paymentDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_008,
                    "Тўлов санаси киритилиши шарт");
        }
        if (data.totalAmount() == null || data.totalAmount().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_001,
                    "Тўлов суммаси мусбат бўлиши шарт");
        }
        if (data.depositAccountId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_002,
                    "Қабул счёти танланиши шарт");
        }
        Account deposit = accountService.get(data.depositAccountId());
        boolean acceptable = deposit.getType() == AccountType.BANK
                || deposit.getDetailType() == AccountDetailType.UNDEPOSITED_FUNDS;
        if (!acceptable || !deposit.isActive() || !deposit.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_002,
                    "Қабул счёти банк/касса/UNDEPOSITED_FUNDS, фаол ва postable "
                    + "бўлиши шарт: " + deposit.getName());
        }
        Currency currency = currencyService.require(
                data.currency() == null || data.currency().isBlank()
                        ? settingsService.homeCurrency() : data.currency());
        // Курс инварианти умумий helper'да (Xorazmiy-005: policy бир жойда),
        // ҳужжатга хос BR код бу ердан берилади
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_RCPT_012);
        // BR-RCPT-002 (Arbitr-070/Nargiza-001): BANK счёт ўз валютасидан
        // бошқа валютадаги тўловни қабул қилмайди (SR BR-SR-002 нақши) -
        // акс ҳолда bankBalances() валюта кесими бузилади. UNDEPOSITED_FUNDS
        // клиринг чўнтагига гаров ҚЎЛЛАНМАЙДИ - чет валюта тўлов QBO'дагидек
        // undeposited орқали қабул қилинади. Курсдан КЕЙИН текширилади -
        // аввалги BR тартиби (012 курс хатоси) ўзгармасин
        if (deposit.getType() == AccountType.BANK
                && deposit.getDetailType() != AccountDetailType.UNDEPOSITED_FUNDS) {
            String accountCurrency = deposit.getCurrency() != null
                    ? deposit.getCurrency().getCode() : settingsService.homeCurrency();
            if (!accountCurrency.equals(currency.getCode())) {
                throw new BusinessRuleException(BusinessRule.BR_RCPT_002,
                        "Қабул счёти валютаси (" + accountCurrency + ") тўлов валютаси ("
                        + currency.getCode() + ") билан бир хил бўлиши шарт: "
                        + deposit.getName());
            }
        }
        return new Normalized(currency, rate);
    }

    /**
     * Тақсимотларнинг умумий йўли: валидация (BR-RCPT-003/004/005/006/
     * 009/011) - ёзув - invoice денормализацияси - курс фарқи JE.
     */
    private void applyAllocations(InvoicePayment payment, List<AllocationData> allocations) {
        BigDecimal allocated = payment.getAllocatedAmount();
        for (AllocationData data : allocations) {
            if (data.amount() == null || data.amount().signum() <= 0) {
                throw new BusinessRuleException(BusinessRule.BR_RCPT_001,
                        "Allocation суммаси мусбат бўлиши шарт");
            }
            Invoice invoice = invoiceRepository.findById(data.invoiceId())
                    .orElseThrow(() -> new NotFoundException(
                            "Invoice топилмади: " + data.invoiceId()));
            validateAllocation(payment, invoice, data.amount());

            allocated = allocated.add(data.amount());
            if (allocated.compareTo(payment.getTotalAmount()) > 0) {
                throw new BusinessRuleException(BusinessRule.BR_RCPT_005,
                        "Allocation'лар йиғиндиси (" + allocated + ") тўлов суммасидан ("
                        + payment.getTotalAmount() + ") ошмайди");
            }
            // saveAndFlush: рўйхат ичидаги такрор ҳам existsBy текширувига
            // дарҳол кўринсин (Persistable.isNew тузоғи ҳам четлаб ўтилади)
            InvoicePaymentAllocation allocation = allocationRepository.saveAndFlush(
                    new InvoicePaymentAllocation(payment, invoice, data.amount()));
            invoice.applyPaidAmount(invoice.getPaidAmount().add(data.amount()));
            postFxDifference(payment, invoice, allocation);
        }
        payment.applyAllocated(allocated);
    }

    /** Битта тақсимот валидацияси - invoice ҳолати, мижоз, валюта, қолдиқ. */
    private void validateAllocation(InvoicePayment payment, Invoice invoice,
                                    BigDecimal amount) {
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_003,
                    "Allocation фақат POSTED invoice'га: " + invoice.getInvoiceNumber()
                    + " ҳозир " + invoice.getStatus());
        }
        if (!invoice.getCustomerId().equals(payment.getCustomerId())) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_009,
                    "Invoice бошқа мижозники: " + invoice.getInvoiceNumber());
        }
        if (!invoice.getCurrency().getCode().equals(payment.getCurrency().getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_006,
                    "Тўлов валютаси (" + payment.getCurrency().getCode()
                    + ") invoice валютаси (" + invoice.getCurrency().getCode()
                    + ") билан бир хил бўлиши шарт: " + invoice.getInvoiceNumber());
        }
        if (amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_004,
                    "Allocation (" + amount + ") invoice қолдиғидан ("
                    + invoice.getBalanceDue() + ") ошмайди: " + invoice.getInvoiceNumber());
        }
        if (allocationRepository.existsByPaymentIdAndInvoiceId(payment.getId(), invoice.getId())) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_011,
                    "Бу тушумдан бу invoice'га allocation аллақачон бор: "
                    + invoice.getInvoiceNumber());
        }
    }

    /**
     * Realized курс фарқи - алоҳида кичик JE (posting-rules «Сотув»).
     * ЙЎНАЛИШИ AP томонга ТЕСКАРИ: фарқ base = allocation × (тўлов
     * курси - invoice курси). Мусбат (base'да кўпроқ тушум олдик) -
     * фойда: AR Dt / курс фарқи счёти Cr; манфий - тескари. Нол фарқ -
     * JE ёзилмайди (home валютада доим нол: иккала курс 1).
     */
    private void postFxDifference(InvoicePayment payment, Invoice invoice,
                                  InvoicePaymentAllocation allocation) {
        BigDecimal diff = Fx.realizedFxDifference(allocation.getAmount(), payment.getExchangeRate(), invoice.getExchangeRate());
        if (diff.signum() == 0) {
            return;
        }
        String home = settingsService.homeCurrency();
        Money value = Money.ofBase(diff.abs(), home);
        UUID ar = accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_RECEIVABLE);
        UUID fx = accountService.requireSystemAccountId(AccountDetailType.EXCHANGE_GAIN_OR_LOSS);
        JournalEntryRequest.Line arLine = diff.signum() > 0
                ? new JournalEntryRequest.Line(ar, value, null,
                        payment.getCustomerId(), null, null, null)
                : new JournalEntryRequest.Line(ar, null, value,
                        payment.getCustomerId(), null, null, null);
        JournalEntryRequest.Line fxLine = diff.signum() > 0
                ? JournalEntryRequest.Line.credit(fx, value, null)
                : JournalEntryRequest.Line.debit(fx, value, null);
        postingService.createAndPost(new JournalEntryRequest(
                payment.getPaymentDate(),
                "Курс фарқи: " + payment.getReceiptNumber() + " → "
                        + invoice.getInvoiceNumber(),
                ALLOCATION_SOURCE_MODULE, allocation.getId(), List.of(arLine, fxLine)));
    }



    /** Тўлов валютасидаги Money - home'да base, чет валютада курс билан. */
    private Money money(InvoicePayment payment, BigDecimal amount) {
        String home = settingsService.homeCurrency();
        return payment.getCurrency().getCode().equals(home)
                ? Money.ofBase(amount, home)
                : Money.of(amount, payment.getCurrency().getCode(),
                        payment.getExchangeRate());
    }

    /** Customer номи - GL тавсифи учун. */
    private String customerName(UUID customerId) {
        return contactService.get(customerId).getDisplayName();
    }

}
