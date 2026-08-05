package com.averpo.erp.purchase.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillPayment;
import com.averpo.erp.purchase.domain.BillPaymentAllocation;
import com.averpo.erp.purchase.domain.BillStatus;
import com.averpo.erp.purchase.repo.BillPaymentAllocationRepository;
import com.averpo.erp.purchase.repo.BillPaymentRepository;
import com.averpo.erp.purchase.repo.BillRepository;
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
 * Vendor тўловининг ягона public API'си (docs/modules/purchases.md).
 * DRAFT йўқ: яратилди = POSTED (QBO услуби), тузатиш reverse орқали.
 * Аванс рухсат - тақсимланмаган қисм AP'да vendor аванси (дебет
 * қолдиқ) бўлиб туради, кейин {@link #allocate} билан ишлатилади.
 *
 * <p>GL - фақат PostingService (ТЕМИР ҚОИДА №2); бошқа модулларга
 * фақат public service орқали (№6). Realized курс фарқи ҳар allocation
 * учун АЛОҲИДА JE - тўлов билан бирга ҳам, кейинги allocation'да ҳам
 * бир хил йўл (posting-rules «Харид»).
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BillPaymentService {

    /** Тўлов GL ёзувининг манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "BILL_PAYMENT";

    /** Курс фарқи JE'ларининг манба модул белгиси - docId = allocation id. */
    public static final String ALLOCATION_SOURCE_MODULE = "PAYMENT_ALLOCATION";

    /** Тўлов формаси маълумотлари - тақсимот бирга келиши ҳам мумкин. */
    public record PaymentData(UUID vendorId, LocalDate paymentDate,
                              UUID bankAccountId, String currency,
                              BigDecimal exchangeRate, BigDecimal totalAmount,
                              String memo, List<AllocationData> allocations) { }

    /** Битта тақсимот: қайси bill'га қанча (тўлов валютасида). */
    public record AllocationData(UUID billId, BigDecimal amount) { }

    /** Тўлов репозиторийси. */
    private final BillPaymentRepository repository;

    /** Тақсимот репозиторийси. */
    private final BillPaymentAllocationRepository allocationRepository;

    /** Bill қолдиқ/денормализацияси учун - ўз модулимиз ичида. */
    private final BillRepository billRepository;

    /** Ҳужжат рақамлари (PAY-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Vendor текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Банк счёти валидацияси ва тизим счётлари (AP, курс фарқи). */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public BillPayment get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тўлов топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 2-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экран тартиби
     * ўзгармасин (Beruniy-perf1, BillService.LIST_SORT қолипи).
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
                             BillPayment.Status status, UUID vendorId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (Beruniy-perf1), тўлиқ филтр
     * (Arbitr-068): давр/статус/vendor/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BillPayment> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("paymentDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("paymentDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("vendorId", filter.vendorId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "paymentNumber", "memo")),
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BillPayment> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Тўловнинг тақсимотлари - кўриш экрани ва тестлар учун. */
    @Transactional(readOnly = true)
    public List<BillPaymentAllocation> allocationsOf(UUID paymentId) {
        return allocationRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    /** Bill'га кетган тақсимотлар - bill кўриш экрани учун. */
    @Transactional(readOnly = true)
    public List<BillPaymentAllocation> allocationsForBill(UUID billId) {
        return allocationRepository.findByBillIdOrderByCreatedAtAsc(billId);
    }

    /**
     * Тўлов яратади - дарҳол POSTED: GL (AP Dt / банк Cr тўлиқ суммага,
     * аванс қисми ҳам шу ёзув ичида) + берилган тақсимотлар. Давр қулфи
     * (BR-LED-020) ва idempotency PostingService'дан автоматик.
     *
     * @throws BusinessRuleException BR-PAY-001..006, 008..012
     */
    public BillPayment create(PaymentData data) {
        Normalized normalized = validate(data);
        BillPayment payment = new BillPayment(
                sequenceService.next(DocumentType.PAYMENT, data.paymentDate()),
                data.vendorId(), data.paymentDate(), data.bankAccountId(),
                normalized.currency(), normalized.rate(), data.totalAmount(),
                Strings.blankToNull(data.memo()));
        repository.saveAndFlush(payment);

        Money amount = money(payment, payment.getTotalAmount());
        postingService.createAndPost(new JournalEntryRequest(
                payment.getPaymentDate(),
                "Тўлов " + payment.getPaymentNumber() + " - " + vendorName(payment.getVendorId()),
                SOURCE_MODULE, payment.getId(), List.of(
                        new JournalEntryRequest.Line(
                                accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_PAYABLE),
                                amount, null, payment.getVendorId(), null, null, null),
                        new JournalEntryRequest.Line(
                                payment.getBankAccountId(),
                                null, amount, payment.getVendorId(), null, null, null))));

        if (data.allocations() != null && !data.allocations().isEmpty()) {
            applyAllocations(payment, data.allocations());
        }
        return payment;
    }

    /**
     * Мавжуд POSTED тўловдан кейинги тақсимот (аванс ишлатиш) - тўлов
     * билан бирга келган тақсимот билан айнан бир хил йўл (spec қарори:
     * бир хиллик, курс фарқи ҳам худди шундай ҳисобланади).
     *
     * @throws BusinessRuleException BR-PAY-003..006, 009, 011, 013
     */
    public BillPayment allocate(UUID paymentId, List<AllocationData> allocations) {
        BillPayment payment = get(paymentId);
        if (payment.getStatus() != BillPayment.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_013,
                    "Allocation фақат POSTED тўловдан: " + payment.getPaymentNumber()
                    + " ҳозир " + payment.getStatus());
        }
        applyAllocations(payment, allocations);
        return payment;
    }

    /**
     * Reverse: тақсимотларнинг курс фарқи JE'лари сторно + bill
     * денормализациялари қайтарилади + тўловнинг ўз GL ёзуви сторно.
     * Тақсимот ёзувлари аудит учун ўчирилмайди - тўлов REVERSED
     * бўлгани уларни бекор қилади (кучга эга эмас).
     *
     * @throws BusinessRuleException BR-PAY-007
     */
    public BillPayment reverse(UUID id, LocalDate reversalDate, String reason) {
        BillPayment payment = get(id);
        if (payment.getStatus() != BillPayment.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_007,
                    "Фақат POSTED тўлов reverse қилинади: " + payment.getPaymentNumber()
                    + " ҳозир " + payment.getStatus());
        }
        String storno = reason == null || reason.isBlank() ? "Тўлов reverse" : reason;
        for (BillPaymentAllocation allocation : allocationsOf(payment.getId())) {
            Bill bill = allocation.getBill();
            // Нол фарқда JE ёзилмаган - фарқни қайта ҳисоблаб аниқлаймиз
            // (формула allocation пайтидагиси билан бир хил, детерминистик)
            if (Fx.realizedFxDifference(allocation.getAmount(), bill.getExchangeRate(), payment.getExchangeRate()).signum() != 0) {
                postingService.reverseBySource(ALLOCATION_SOURCE_MODULE,
                        allocation.getId(), reversalDate, storno);
            }
            bill.applyPaidAmount(bill.getPaidAmount().subtract(allocation.getAmount()));
        }
        postingService.reverseBySource(SOURCE_MODULE, payment.getId(),
                reversalDate, storno);
        // Bill томони юқорида тикланди - тўловнинг ўз денормализацияси ҳам
        // тикланади: REVERSED тўлов аванс/тақсимот рўйхатларида «40 бор»
        // деб адаштирмасин (тақсимот ёзувлари аудит учун қолаверади)
        payment.applyAllocated(BigDecimal.ZERO);
        payment.markReversed();
        return payment;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate) { }

    /** Тўлов сарлавҳаси валидацияси (BR-PAY-001/002/008/010/012). */
    private Normalized validate(PaymentData data) {
        if (data.vendorId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_010,
                    "Vendor танланиши шарт");
        }
        Contact vendor = contactService.get(data.vendorId());
        if (vendor.getType() != ContactType.VENDOR || !vendor.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_010,
                    "Vendor фаол VENDOR типдаги контакт бўлиши шарт: " + vendor.getDisplayName());
        }
        if (data.paymentDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_008,
                    "Тўлов санаси киритилиши шарт");
        }
        if (data.totalAmount() == null || data.totalAmount().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_001,
                    "Тўлов суммаси мусбат бўлиши шарт");
        }
        if (data.bankAccountId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_002,
                    "Тўлов банк счёти танланиши шарт");
        }
        Account bank = accountService.get(data.bankAccountId());
        if (bank.getType() != AccountType.BANK || !bank.isActive() || !bank.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_002,
                    "Счёт BANK туридан, фаол ва postable бўлиши шарт: " + bank.getName());
        }
        Currency currency = currencyService.require(
                data.currency() == null || data.currency().isBlank()
                        ? settingsService.homeCurrency() : data.currency());
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_PAY_012);
        // BR-PAY-002 (Arbitr-070/Nargiza-001): банк счёти ўз валютасидан
        // бошқа валютадаги тўловни ёзмайди (SR BR-SR-002 / InvoicePayment
        // нақши) - акс ҳолда bankBalances() валюта кесими бузилади. Курсдан
        // КЕЙИН текширилади - аввалги BR тартиби (012 курс хатоси) ўзгармасин
        String accountCurrency = bank.getCurrency() != null
                ? bank.getCurrency().getCode() : settingsService.homeCurrency();
        if (!accountCurrency.equals(currency.getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_002,
                    "Тўлов счёти валютаси (" + accountCurrency + ") тўлов валютаси ("
                    + currency.getCode() + ") билан бир хил бўлиши шарт: "
                    + bank.getName());
        }
        return new Normalized(currency, rate);
    }

    /**
     * Тақсимотларнинг умумий йўли: валидация (BR-PAY-003/004/005/006/
     * 009/011) - ёзув - bill денормализацияси - курс фарқи JE.
     */
    private void applyAllocations(BillPayment payment, List<AllocationData> allocations) {
        BigDecimal allocated = payment.getAllocatedAmount();
        for (AllocationData data : allocations) {
            if (data.amount() == null || data.amount().signum() <= 0) {
                throw new BusinessRuleException(BusinessRule.BR_PAY_001,
                        "Allocation суммаси мусбат бўлиши шарт");
            }
            Bill bill = billRepository.findById(data.billId())
                    .orElseThrow(() -> new NotFoundException("Bill топилмади: " + data.billId()));
            validateAllocation(payment, bill, data.amount());

            allocated = allocated.add(data.amount());
            if (allocated.compareTo(payment.getTotalAmount()) > 0) {
                throw new BusinessRuleException(BusinessRule.BR_PAY_005,
                        "Allocation'лар йиғиндиси (" + allocated + ") тўлов суммасидан ("
                        + payment.getTotalAmount() + ") ошмайди");
            }
            // saveAndFlush: рўйхат ичидаги такрор ҳам existsBy текширувига
            // дарҳол кўринсин (Persistable.isNew тузоғи ҳам четлаб ўтилади)
            BillPaymentAllocation allocation = allocationRepository.saveAndFlush(
                    new BillPaymentAllocation(payment, bill, data.amount()));
            bill.applyPaidAmount(bill.getPaidAmount().add(data.amount()));
            postFxDifference(payment, bill, allocation);
        }
        payment.applyAllocated(allocated);
    }

    /** Битта тақсимот валидацияси - bill ҳолати, vendor, валюта, қолдиқ. */
    private void validateAllocation(BillPayment payment, Bill bill, BigDecimal amount) {
        if (bill.getStatus() != BillStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_003,
                    "Allocation фақат POSTED bill'га: " + bill.getBillNumber()
                    + " ҳозир " + bill.getStatus());
        }
        if (!bill.getVendorId().equals(payment.getVendorId())) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_009,
                    "Bill бошқа vendor'ники: " + bill.getBillNumber());
        }
        if (!bill.getCurrency().getCode().equals(payment.getCurrency().getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_006,
                    "Тўлов валютаси (" + payment.getCurrency().getCode()
                    + ") bill валютаси (" + bill.getCurrency().getCode()
                    + ") билан бир хил бўлиши шарт: " + bill.getBillNumber());
        }
        if (amount.compareTo(bill.getBalanceDue()) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_004,
                    "Allocation (" + amount + ") bill қолдиғидан ("
                    + bill.getBalanceDue() + ") ошмайди: " + bill.getBillNumber());
        }
        if (allocationRepository.existsByPaymentIdAndBillId(payment.getId(), bill.getId())) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_011,
                    "Бу тўловдан бу bill'га allocation аллақачон бор: " + bill.getBillNumber());
        }
    }

    /**
     * Realized курс фарқи - алоҳида кичик JE (posting-rules «Харид»):
     * фарқ base = allocation × (bill курси - тўлов курси). Мусбат
     * (AP'даги қарз base'и тўловдан катта) - фойда: AP Dt / курс фарқи
     * счёти Cr; манфий - тескари. Нол фарқ - JE ёзилмайди (home
     * валютада доим нол: иккала курс 1).
     */
    private void postFxDifference(BillPayment payment, Bill bill,
                                  BillPaymentAllocation allocation) {
        BigDecimal diff = Fx.realizedFxDifference(allocation.getAmount(), bill.getExchangeRate(), payment.getExchangeRate());
        if (diff.signum() == 0) {
            return;
        }
        String home = settingsService.homeCurrency();
        Money value = Money.ofBase(diff.abs(), home);
        UUID ap = accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_PAYABLE);
        UUID fx = accountService.requireSystemAccountId(AccountDetailType.EXCHANGE_GAIN_OR_LOSS);
        JournalEntryRequest.Line apLine = diff.signum() > 0
                ? new JournalEntryRequest.Line(ap, value, null,
                        payment.getVendorId(), null, null, null)
                : new JournalEntryRequest.Line(ap, null, value,
                        payment.getVendorId(), null, null, null);
        JournalEntryRequest.Line fxLine = diff.signum() > 0
                ? JournalEntryRequest.Line.credit(fx, value, null)
                : JournalEntryRequest.Line.debit(fx, value, null);
        postingService.createAndPost(new JournalEntryRequest(
                payment.getPaymentDate(),
                "Курс фарқи: " + payment.getPaymentNumber() + " → " + bill.getBillNumber(),
                ALLOCATION_SOURCE_MODULE, allocation.getId(), List.of(apLine, fxLine)));
    }



    /** Тўлов валютасидаги Money - home'да base, чет валютада курс билан. */
    private Money money(BillPayment payment, BigDecimal amount) {
        String home = settingsService.homeCurrency();
        return payment.getCurrency().getCode().equals(home)
                ? Money.ofBase(amount, home)
                : Money.of(amount, payment.getCurrency().getCode(),
                        payment.getExchangeRate());
    }

    /** Vendor номи - GL тавсифи учун. */
    private String vendorName(UUID vendorId) {
        return contactService.get(vendorId).getDisplayName();
    }

}
