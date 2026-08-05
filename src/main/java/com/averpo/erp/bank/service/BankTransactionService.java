package com.averpo.erp.bank.service;

import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.domain.BankTransactionLine;
import com.averpo.erp.bank.domain.BankTransactionType;
import com.averpo.erp.bank.repo.BankTransactionRepository;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.domain.MoneyAllocation;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import com.averpo.erp.shared.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Банк транзакцияларининг ягона public API'си (docs/modules/banking.md):
 * DEPOSIT (кўп сатрли кирим, QBO Bank Deposit) / EXPENSE (AP'сиз тўғри
 * чиқим) / TRANSFER (ўтказма; валюта конверсиясида base фарқи
 * EXCHANGE_GAIN_OR_LOSS'га). DRAFT йўқ - яратилди = POSTED, тузатиш
 * reverse орқали.
 *
 * <p>Ҳужжат валютаси БАНК СЧЁТИДАН келади (танланмайди, QBO услуби):
 * Account.currency бўш бўлса home. GL - фақат PostingService (ТЕМИР
 * ҚОИДА №2); бошқа модулларга фақат public service'лар орқали (№6).
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BankTransactionService {

    /** GL манба модул белгиси (posting-rules «Банк»). */
    public static final String SOURCE_MODULE = "BANK_TXN";

    /** DEPOSIT/EXPENSE сатри: счёт + сумма (банк валютасида) + ихтиёрий контакт. */
    public record LineData(UUID accountId, BigDecimal amount,
                           UUID contactId, String memo, UUID classId) {

        /** Эски 4 майдонли имзо - class'сиз чақирувлар. */
        public LineData(UUID accountId, BigDecimal amount,
                        UUID contactId, String memo) {
            this(accountId, amount, contactId, memo, null);
        }
    }

    /**
     * DEPOSIT/EXPENSE формаси маълумотлари. paymentMethodId/refNo -
     * Arbitr-033 (QBO PaymentMethodRef/DocNumber), иккиси ихтиёрий;
     * deposit ҳам қабул қилади (формасида ҳозирча кўрсатилмайди).
     */
    public record TxnData(UUID bankAccountId, LocalDate date,
                          BigDecimal exchangeRate, UUID contactId,
                          String memo, List<LineData> lines,
                          UUID paymentMethodId, String refNo) {

        /** Эски имзо - тўлов реквизитисиз чақирувлар (мавжуд тестлар) учун. */
        public TxnData(UUID bankAccountId, LocalDate date,
                       BigDecimal exchangeRate, UUID contactId,
                       String memo, List<LineData> lines) {
            this(bankAccountId, date, exchangeRate, contactId, memo, lines, null, null);
        }
    }

    /**
     * TRANSFER формаси: манба/манзил банк ва ҳар томон суммаси/курси.
     * Бир валютада to* қийматлари эътиборга олинмайди (from билан тенг
     * олинади); конверсияда иккаласи шарт.
     */
    public record TransferData(UUID fromBankAccountId, UUID toBankAccountId,
                               LocalDate date, BigDecimal fromAmount,
                               BigDecimal fromRate, BigDecimal toAmount,
                               BigDecimal toRate, String memo) { }

    /** Транзакциялар репозиторийси. */
    private final BankTransactionRepository repository;

    /** Ҳужжат рақамлари (BT-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Банк/сатр счётлари валидацияси ва FX тизим счёти. */
    private final AccountService accountService;

    /** Ихтиёрий контакт (payee) мавжудлигини текшириш учун. */
    private final ContactService contactService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Ўтказмада ишлатилган ҳақиқий курсни каталогга ёзиш учун (Т3). */
    private final ExchangeRateService exchangeRateService;

    /** Тўлов усули мавжудлигини текшириш учун (Arbitr-033, қоида №6). */
    private final com.averpo.erp.shared.service.PaymentMethodService paymentMethodService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public BankTransaction get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Транзакция топилмади: " + id));
    }

    /** Кўриш учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public BankTransaction getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Транзакция топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 2-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экрандаги тартиб
     * ўзгармасин. list() ва expenses() учун битта манба (Beruniy-perf1).
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("txnDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Рўйхат филтри (Arbitr-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - txn рақами/банк ҳужжат ҳаваласи
     * (ref_no)/изоҳ contains (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             BankTransaction.Status status, UUID contactId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (Beruniy-perf1), тўлиқ филтр
     * (Arbitr-068): давр/статус/контакт/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари). Банк журнали энг тез ўсувчи
     * рўйхат - LIMIT/OFFSET SQL'да қолади.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankTransaction> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                com.averpo.erp.shared.repo.ListSpecs.dateFrom("txnDate", filter.from()),
                com.averpo.erp.shared.repo.ListSpecs.dateTo("txnDate", filter.to()),
                com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                com.averpo.erp.shared.repo.ListSpecs.eq("contactId", filter.contactId()),
                com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                        "txnNumber", "refNo", "memo")), pageRequest(page, size));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankTransaction> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /**
     * Ўтказмалар рўйхати (Beruniy-020): фақат TRANSFER; Arbitr-068 билан
     * давр/статус/матн филтри қўшилди - Specification'да, валюта fetch
     * сақланган (N+1 йўқ; List йўли - count сўрови йўқ, to-one fetch
     * хавфсиз). Ўтказма камроқ ёзилади - саҳифалаш 3-босқичда (perf1).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankTransaction> transfers(
            ListFilter filter, int page, int size) {
        // Валюта JOIN FETCH (Beruniy-020 N+1 сабоғи) - лекин ARBITR-105:
        // Page count сўровида fetch ЙЎҚ (count'да JOIN FETCH хато беради;
        // to-one fetch фақат DATA сўровида, pagination'ни бузмайди)
        org.springframework.data.jpa.domain.Specification<BankTransaction> withCurrency =
                (root, query, cb) -> {
                    if (query.getResultType() != Long.class
                            && query.getResultType() != long.class) {
                        root.fetch("currency");
                    }
                    return null;
                };
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        withCurrency,
                        com.averpo.erp.shared.repo.ListSpecs.eq("type",
                                BankTransactionType.TRANSFER),
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("txnDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("txnDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "txnNumber", "refNo", "memo")),
                pageRequest(page, size));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankTransaction> transfers(ListFilter filter, int page) {
        return transfers(filter, page, LIST_PAGE_SIZE);
    }

    /**
     * Чиқимлар рўйхати (Arbitr-033) - саҳифаланган (Beruniy-perf1); Arbitr-068
     * билан давр/статус/payee/матн филтри уланди: {@link #transfers} қолипи,
     * фарқи фақат {@code type=EXPENSE} ва payee (contactId) бўлаги - чиқимда
     * Олувчи бор ({@link #list} даги eq("contactId") кўзгуси), ўтказма
     * контактсиз. Валюта fetch сақланган (Beruniy-020 N+1 йўқ; count сўровида
     * fetch ЙЎҚ - ARBITR-105).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankTransaction> expenses(
            ListFilter filter, int page, int size) {
        org.springframework.data.jpa.domain.Specification<BankTransaction> withCurrency =
                (root, query, cb) -> {
                    if (query.getResultType() != Long.class
                            && query.getResultType() != long.class) {
                        root.fetch("currency");
                    }
                    return null;
                };
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        withCurrency,
                        com.averpo.erp.shared.repo.ListSpecs.eq("type",
                                BankTransactionType.EXPENSE),
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("txnDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("txnDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("contactId", filter.contactId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "txnNumber", "refNo", "memo")),
                pageRequest(page, size));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BankTransaction> expenses(ListFilter filter, int page) {
        return expenses(filter, page, LIST_PAGE_SIZE);
    }

    /** Саҳифа сўрови (LIST_SORT билан) - манфий саҳифа рақами 0'га қисилади; size - ARBITR-105. */
    private static org.springframework.data.domain.PageRequest pageRequest(int page, int size) {
        return org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, LIST_SORT);
    }

    /**
     * Кирим (QBO Bank Deposit): банк Dt (жами) / ҳар сатр манба счёти
     * Cr. Типик ҳол: Тушумлар транзитидан банкка.
     *
     * @throws BusinessRuleException BR-BT-001..004, 006, 008
     */
    public BankTransaction deposit(TxnData data) {
        return createLinedTransaction(BankTransactionType.DEPOSIT, data);
    }

    /**
     * Чиқим (QBO Expense): ҳар сатр счёти Dt / банк Cr (жами) -
     * AP оқимисиз тўғридан-тўғри тўлов.
     *
     * @throws BusinessRuleException BR-BT-001..004, 006, 008
     */
    public BankTransaction expense(TxnData data) {
        return createLinedTransaction(BankTransactionType.EXPENSE, data);
    }

    /**
     * Ўтказма: манзил банк Dt / манба банк Cr. Валюта конверсиясида
     * base фарқи EXCHANGE_GAIN_OR_LOSS сатри билан тенгланади (олинган
     * base кўп - фойда FX Cr, кам - зарар FX Dt); айнан тенг base'да
     * FX сатр ёзилмайди. Бир валютада тўғридан-тўғри ўтказма.
     *
     * @throws BusinessRuleException BR-TXF-001, BR-BT-001/005/006/008
     */
    public BankTransaction transfer(TransferData data) {
        Account from = requireTransferAccount(data.fromBankAccountId());
        Account to = requireTransferAccount(data.toBankAccountId());
        if (from.getId().equals(to.getId())) {
            throw new BusinessRuleException(BusinessRule.BR_BT_005,
                    "Манба ва манзил счёти ҳар хил бўлиши шарт: " + from.getName());
        }
        requireDate(data.date());
        requirePositive(data.fromAmount(), "Ўтказма суммаси");

        Currency fromCurrency = accountCurrency(from);
        Currency toCurrency = accountCurrency(to);
        BigDecimal fromRate = currencyService.requireDocumentRate(
                fromCurrency, data.fromRate(), BusinessRule.BR_BT_008);
        boolean sameCurrency = fromCurrency.getCode().equals(toCurrency.getCode());
        // Бир валютада манзил томон қийматлари манбадан олинади -
        // фойдаланувчи киритган бўлса ҳам эътиборга олинмайди
        BigDecimal toAmount = sameCurrency ? data.fromAmount() : data.toAmount();
        BigDecimal toRate = sameCurrency ? fromRate
                : currencyService.requireDocumentRate(
                        toCurrency, data.toRate(), BusinessRule.BR_BT_008);
        requirePositive(toAmount, "Манзил томон суммаси");

        BankTransaction txn = new BankTransaction(
                sequenceService.next(DocumentType.BANK_TXN, data.date()),
                BankTransactionType.TRANSFER, from.getId(), to.getId(),
                data.date(), fromCurrency, fromRate, toAmount, toRate,
                null, Strings.blankToNull(data.memo()));
        txn.applyTransferTotal(data.fromAmount());
        repository.saveAndFlush(txn);

        String home = settingsService.homeCurrency();
        Money credit = money(data.fromAmount(), fromCurrency, fromRate, home);
        Money debit = money(toAmount, toCurrency, toRate, home);
        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        glLines.add(new JournalEntryRequest.Line(to.getId(), debit, null,
                null, null, null, null));
        glLines.add(new JournalEntryRequest.Line(from.getId(), null, credit,
                null, null, null, null));
        // Конверсия фарқи: олинган base - берилган base
        BigDecimal diff = debit.getBaseAmount().subtract(credit.getBaseAmount());
        if (diff.signum() != 0) {
            UUID fx = accountService.requireSystemAccountId(AccountDetailType.EXCHANGE_GAIN_OR_LOSS);
            Money value = Money.ofBase(diff.abs(), home);
            glLines.add(diff.signum() > 0
                    ? JournalEntryRequest.Line.credit(fx, value, null)
                    : JournalEntryRequest.Line.debit(fx, value, null));
        }
        postingService.createAndPost(new JournalEntryRequest(
                data.date(), "Ўтказма " + txn.getTxnNumber() + ": " + from.getName()
                        + " → " + to.getName(),
                SOURCE_MODULE, txn.getId(), glLines));

        // Т3 (docs/modules/transfer.md, Arbitr-022): айнан битта ноёб чет
        // валюта иштирок этса, ўтказмада ишлатилган курс каталогга ҳужжат
        // санаси билан upsert қилинади - фойдаланувчи ҳақиқий курсни қайд
        // этади (қўлда киритилган курс устун). Икки хил чет валюта -
        // кросс-курс уй валютасига нисбатан эмас, ёзилмайди.
        boolean fromForeign = !fromCurrency.getCode().equals(home);
        boolean toForeign = !toCurrency.getCode().equals(home);
        if ((fromForeign != toForeign) || (fromForeign && sameCurrency)) {
            Currency foreign = fromForeign ? fromCurrency : toCurrency;
            BigDecimal foreignRate = fromForeign ? fromRate : toRate;
            exchangeRateService.upsert(foreign.getCode(), data.date(), foreignRate);
        }
        return txn;
    }

    /**
     * Reverse: оддий GL сторно (омбор/денормализация йўқ). Reconcile
     * қилинган транзакция сторноси кейинги reconcile'да белгиланади
     * (QBO услуби, ҳимоя шарт эмас).
     *
     * @throws BusinessRuleException BR-BT-007
     */
    public BankTransaction reverse(UUID id, LocalDate reversalDate, String reason) {
        BankTransaction txn = get(id);
        if (txn.getStatus() != BankTransaction.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_BT_007,
                    "Фақат POSTED транзакция reverse қилинади: " + txn.getTxnNumber()
                    + " ҳозир " + txn.getStatus());
        }
        postingService.reverseBySource(SOURCE_MODULE, id, reversalDate,
                reason == null || reason.isBlank() ? "Транзакция reverse" : reason);
        txn.markReversed();
        return txn;
    }

    // ---- ички ёрдамчилар ----

    /** DEPOSIT ва EXPENSE'нинг умумий йўли - фақат Dt/Cr томони фарқ қилади. */
    private BankTransaction createLinedTransaction(BankTransactionType type, TxnData data) {
        Account bank = requireBankAccount(data.bankAccountId());
        requireDate(data.date());
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_BT_003,
                    type + " транзакциясида камида битта сатр бўлиши шарт");
        }
        Currency currency = accountCurrency(bank);
        // Курс инварианти умумий helper'да (Xorazmiy-005: policy бир жойда)
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_BT_008);
        if (data.contactId() != null) {
            contactService.get(data.contactId()); // мавжудлик (NotFound)
        }
        if (data.paymentMethodId() != null) {
            // Мавжудлик NotFound билан - янги BR кодисиз (Arbitr-033);
            // нофаоллик select даражасида (формада кўринмайди)
            paymentMethodService.get(data.paymentMethodId());
        }

        BankTransaction txn = new BankTransaction(
                sequenceService.next(DocumentType.BANK_TXN, data.date()),
                type, bank.getId(), null, data.date(), currency, rate,
                null, null, data.contactId(), Strings.blankToNull(data.memo()));
        txn.applyPaymentDetails(data.paymentMethodId(),
                Strings.blankToNull(data.refNo()));
        // Батч lookup (Arbitr-045 findAllById, Sanjar-008): сатр-циклда
        // счёт/контакт биттадан ўқилмасин - id'лар олдиндан йиғилиб
        // иккита IN сўров билан Map'га олинади, циклда Map.get()
        Map<UUID, Account> accounts = BatchLookup.byId(
                accountService.findAllById(BatchLookup.ids(data.lines(), LineData::accountId)));
        Map<UUID, Contact> contacts = BatchLookup.byId(
                contactService.findAllById(BatchLookup.ids(data.lines(), LineData::contactId)));
        int no = 0;
        for (LineData line : data.lines()) {
            no++;
            requirePositive(line.amount(), no + "-сатр суммаси");
            if (line.accountId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_BT_004,
                        no + "-сатр: счёт танланиши шарт");
            }
            Account account = accounts.get(line.accountId());
            if (account == null) {
                throw new NotFoundException("Счёт топилмади: " + line.accountId());
            }
            if (!account.isActive() || !account.isPostable()
                    || account.getId().equals(bank.getId())) {
                throw new BusinessRuleException(BusinessRule.BR_BT_004,
                        no + "-сатр: счёт фаол, postable ва банкнинг ўзи эмаслиги "
                        + "шарт: " + account.getName());
            }
            // BR-BT-010 (Xorazmiy-012, BR-TXF-002 кўзгуси): тизим назорат
            // счётига қўлда кирим/чиқим сатри GL'ни subledger'сиз
            // ўзгартиради (StockMovement/aging четда қолади). Истисно -
            // UNDEPOSITED_FUNDS: кирим/чиқим айнан унинг ўз оқими (QBO
            // Bank Deposit, «транзитдан банкка»), алоҳида subledger'и йўқ.
            // SALES_TAX_PAYABLE systemManaged ЭМАС - ҚҚС тўлови шу оқимда
            // очиқ (AccountDetailType JavaDoc'и).
            if (account.getDetailType().systemManaged()
                    && account.getDetailType() != AccountDetailType.UNDEPOSITED_FUNDS) {
                throw new BusinessRuleException(BusinessRule.BR_BT_010,
                        no + "-сатр: тизим назорат счётига ёзилмайди - у фақат "
                        + "ўз ҳужжат оқими орқали ёзилади: " + account.getName());
            }
            if (line.contactId() != null && contacts.get(line.contactId()) == null) {
                // мавжудлик (NotFound) - аввалги get() хулқи айнан
                throw new NotFoundException("Контакт топилмади: " + line.contactId());
            }
            txn.addLine(line.accountId(), line.amount(), line.contactId(),
                    Strings.blankToNull(line.memo()))
                    .applyClass(line.classId());
        }
        repository.saveAndFlush(txn);

        String home = settingsService.homeCurrency();
        boolean isHome = currency.getCode().equals(home);
        boolean depositType = type == BankTransactionType.DEPOSIT;
        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        // Penny rounding (Beruniy-009 + Asrorxoja-002): банк томони
        // (назорат сатри) base'и total × rate'нинг БИТТА яхлитлаши
        // (MoneyAllocation.targetBase - BR-LED-003 аниқ сақланади),
        // сатр base'лари эса largest-remainder билан айнан шу target'га
        // тақсимланади - ҳар сатр четлашиши ≤ 0.0001, йиғинди айнан
        // target (BR-LED-006). Сатр йиғиндисини банк томонига олиш
        // ярамас эди - N ≥ 3 да банк сатри BR-LED-003 дан чиқарди.
        List<BankTransactionLine> txnLines = txn.getLines();
        List<BigDecimal> lineBases = null;
        if (!isHome) {
            List<BigDecimal> amounts = new ArrayList<>(txnLines.size());
            for (BankTransactionLine line : txnLines) {
                amounts.add(line.getAmount());
            }
            lineBases = MoneyAllocation.lineBases(amounts, rate);
        }
        for (int i = 0; i < txnLines.size(); i++) {
            BankTransactionLine line = txnLines.get(i);
            Money amount = isHome
                    ? Money.ofBase(line.getAmount(), home)
                    : Money.withBase(line.getAmount(), currency.getCode(),
                            lineBases.get(i), rate);
            // Class сатрдан ўз легига кўчади; банк жами сатри class'сиз
            glLines.add(new JournalEntryRequest.Line(line.getAccountId(),
                    depositType ? null : amount, depositType ? amount : null,
                    line.getContactId(), null, null, line.getMemo(),
                    line.getClassId()));
        }
        // Банк томони рўйхат бошида туради: deposit'да Dt, expense'да Cr
        Money total = isHome
                ? Money.ofBase(txn.getTotal(), home)
                : Money.withBase(txn.getTotal(), currency.getCode(),
                        MoneyAllocation.targetBase(txn.getTotal(), rate), rate);
        glLines.add(0, new JournalEntryRequest.Line(bank.getId(),
                depositType ? total : null, depositType ? null : total,
                txn.getContactId(), null, null, null));
        postingService.createAndPost(new JournalEntryRequest(
                data.date(),
                (depositType ? "Кирим " : "Чиқим ") + txn.getTxnNumber()
                        + " - " + bank.getName(),
                SOURCE_MODULE, txn.getId(), glLines));
        return txn;
    }

    /** BR-BT-002: банк счёти BANK туридан, фаол ва postable. */
    private Account requireBankAccount(UUID accountId) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_BT_002,
                    "Банк счёти танланиши шарт");
        }
        Account account = accountService.get(accountId);
        if (account.getType() != AccountType.BANK || !account.isActive()
                || !account.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_BT_002,
                    "Счёт BANK туридан, фаол ва postable бўлиши шарт: "
                    + account.getName());
        }
        return account;
    }

    /**
     * BR-TXF-001: ўтказма счёти - Balance Sheet (Актив/Мажбурият/Капитал),
     * фаол ва postable. {@link #requireBankAccount} дан фарқи: транзфер
     * QBO услубида ({@code Must be a Balance Sheet account}) ҳар қандай
     * Balance Sheet счёти орасида бўлади, фақат BANK эмас - масалан
     * банкдан кассага, заёмни ёпиш ёки капитал киритиш
     * (docs/modules/transfer.md).
     *
     * <p>BR-TXF-002 (Komil-008): тизим-бошқарув назорат счёти
     * ({@link AccountDetailType#systemManaged()}) манба ҳам, манзил ҳам
     * бўлолмайди - қўлда ўтказма GL қолдиғини ўзгартиради, subledger'ни
     * (AR/AP aging, StockMovement valuation) эмас, мувофиқлик бузилади.
     */
    private Account requireTransferAccount(UUID accountId) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_TXF_001,
                    "Ўтказма счёти танланиши шарт");
        }
        Account account = accountService.get(accountId);
        if (!account.getClassification().isBalanceSheet() || !account.isActive()
                || !account.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_TXF_001,
                    "Счёт Balance Sheet (Актив/Мажбурият/Капитал), фаол ва "
                    + "postable бўлиши шарт: " + account.getName());
        }
        if (account.getDetailType().systemManaged()) {
            throw new BusinessRuleException(BusinessRule.BR_TXF_002,
                    "Тизим назорат счётига ўтказма қилинмайди - у фақат ўз "
                    + "ҳужжат оқими орқали ёзилади: " + account.getName());
        }
        return account;
    }

    /** Ҳужжат валютаси банк счётидан: Account.currency бўш бўлса home. */
    private Currency accountCurrency(Account account) {
        return account.getCurrency() != null ? account.getCurrency()
                : currencyService.require(settingsService.homeCurrency());
    }

    /** BR-BT-001: сумма мусбат. */
    private void requirePositive(BigDecimal amount, String label) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_BT_001,
                    label + " мусбат бўлиши шарт");
        }
    }

    /** BR-BT-006: сана шарт. */
    private void requireDate(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleException(BusinessRule.BR_BT_006,
                    "Транзакция санаси киритилиши шарт");
        }
    }

    /** Ҳужжат валютасидаги Money - home'да base, чет валютада курс билан. */
    private Money money(BigDecimal amount, Currency currency, BigDecimal rate,
                        String home) {
        return currency.getCode().equals(home)
                ? Money.ofBase(amount, home)
                : Money.of(amount, currency.getCode(), rate);
    }


}
