package com.averpo.erp.ledger.service;

import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PostingService имплементацияси.
 *
 * <p>Валидация тартиби docs/modules/ledger.md га қатъий мос: (1) сатр
 * сони, (2) XOR, (3) счёт ҳолати, (4) баланс - фақат post'да,
 * (5) Money инварианти, (6) статус. Хатолар аниқ хабарли
 * {@link PostingException} билан чиқади - чақирувчи модул фойдаланувчига
 * шу матнни кўрсата олади.
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
class PostingServiceImpl implements PostingService {

    /**
     * Проводка изи логгери (docs/modules/logging.md, Arbitr-099): молия
     * юраги - ҳар post/reverse INFO'да манба тури, ҳужжат рақами ва home
     * жами билан изли қолади (муаммо ташхисида «қайси ҳужжат» дарҳол
     * кўринади). GL суммаларига таъсир йўқ - фақат кузатув.
     */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PostingServiceImpl.class);

    /** Money инварианти учун: |baseAmount - amount*rate| шундан ошмасин. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    /** Entry сақлаш ва рақам sequence'и. */
    private final JournalEntryRepository entryRepository;

    /** Сатрлардаги счётларни резолюция қилиш учун. */
    private final AccountRepository accountRepository;

    /** Home currency - сатр валютаси home бўлса курс 1 бўлиши шарт. */
    private final CompanySettingsService settingsService;

    /** Money'даги валюта коди каталогга қарши текширилади (draft'да). */
    private final com.averpo.erp.shared.service.CurrencyService currencyService;

    /** JE рақамлари умумий ҳужжат рақамлаш service'идан (014 changeset). */
    private final com.averpo.erp.shared.service.DocumentSequenceService sequenceService;

    /**
     * Post/reverse ҳодисаларини эълон қилиш учун (audit-log.md): ledger
     * ҳеч кимга боғланмайди (қоида №6) - audit модули event'ни ўзи
     * тинглайди. Синхрон publish - тингловчи шу транзакцияда ёзади.
     */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Class фаоллиги гарови (BR-CLS-001, class-tracking.md) - каталог
     * shared'да тургани учун ledger боғлана олади (қоида №6 сақланади).
     */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** {@inheritDoc} */
    @Override
    public JournalEntry createDraft(JournalEntryRequest request) {
        validateStructure(request);
        // Idempotency guard: бир манба ҳужжат GL'га икки марта тушмасин -
        // invoice/payment post қайта чақирилса энг оғир бухгалтерия
        // хатоси бўлар эди. Бу текширув аниқ хабар учун; parallel race'га
        // қарши ҳақиқий кафолат - DB'даги ux_je_source_active partial
        // unique index (пастдаги saveAndFlush уни тутади). reverse() бу
        // йўлдан ўтмайди: сторно ўша source билан атайлаб ёзилади,
        // index'дан reversal_of_id орқали четда қолади.
        if (request.sourceDocumentId() != null
                && entryRepository.existsBySourceModuleAndSourceDocumentIdAndStatusInAndReversalOfIsNull(
                        request.sourceModule(), request.sourceDocumentId(),
                        java.util.List.of(EntryStatus.DRAFT, EntryStatus.POSTED))) {
            throw new PostingException(BusinessRule.BR_LED_012, "Бу манба ҳужжат аллақачон GL'да: "
                    + request.sourceModule() + "/" + request.sourceDocumentId());
        }

        JournalEntry entry = new JournalEntry(
                nextNumber(request.entryDate()),
                request.entryDate(),
                request.description(),
                request.sourceModule(),
                request.sourceDocumentId());

        for (JournalEntryRequest.Line line : request.lines()) {
            Account account = resolvePostableAccount(line.accountId());
            entry.addLine(account, line.debit(), line.credit(),
                    line.contactId(), line.warehouseId(), line.itemId(), line.memo(),
                    line.classId());
        }
        try {
            // flush шу ерда: index бузилишини commit'га қолдирмай дарҳол
            // тутиб, чақирувчига аниқ BR коди билан қайтарамиз.
            return entryRepository.saveAndFlush(entry);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (request.sourceDocumentId() != null && isSourceUniqueViolation(e)) {
                throw new PostingException(BusinessRule.BR_LED_012, "Бу манба ҳужжат аллақачон GL'да: "
                        + request.sourceModule() + "/" + request.sourceDocumentId());
            }
            throw e;
        }
    }

    /**
     * DataIntegrityViolation айнан ux_je_source_active index'дан
     * келганини аниқлайди - бошқа constraint бузилишларини BR-LED-012
     * деб ёлғон белгилаб қўймаслик учун хабар матни текширилади.
     */
    private boolean isSourceUniqueViolation(org.springframework.dao.DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("ux_je_source_active")) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public JournalEntry post(UUID entryId) {
        JournalEntry entry = load(entryId);
        if (entry.getStatus() != EntryStatus.DRAFT) {
            throw new PostingException(BusinessRule.BR_LED_007,
                    "Фақат DRAFT entry post қилинади: " + entry.getEntryNumber()
                    + " ҳозир " + entry.getStatus());
        }
        // Draft яратилгандан КЕЙИН ёпилиш санаси сурилган бўлиши мумкин -
        // post олдидан period lock қайта текширилади (BR-LED-020)
        requireOpenPeriod(entry.getEntryDate());
        // Draft яратилгандан кейин счёт ҳолати ўзгарган бўлиши мумкин
        // (нофаол қилинган, гуруҳга айлантирилган) - post олдидан
        // ҲАММА инвариант қайта текширилади (ledger.md, post вақтида).
        revalidateLines(entry, true);
        validateBalance(entry);
        entry.markPosted(Instant.now());
        // Техник log (Arbitr-099): манба тури + рақам + home жами. Аудит
        // ҳодисасидан алоҳида - developer файл изи (audit UI'да эмас).
        log.info("JE POSTED: {} {} home жами {}", sourceLabel(entry),
                entry.getEntryNumber(), homeTotal(entry));
        // Аудит ҳодисаси айнан шу ерда: createAndPost ҳам, draft'ни
        // алоҳида post қилиш ҳам шу нуқтадан ўтади - қамров тўлиқ
        // (audit-log.md «битта нуқта = тўлиқ қамров»)
        eventPublisher.publishEvent(new JournalEntryPostedEvent(entry));
        return entry;
    }

    /**
     * Post олдидан entity сатрларини тўлиқ қайта валидация қилади.
     *
     * @param revalidateClasses post'да true (draft'дан кейин class нофаол
     *        қилинган бўлиши мумкин - BR-CLS-001 қайта, BR-TAX-003 нақши);
     *        reverse'да false - сторно тарихий тегни кўчиради, нофаол
     *        class сторнони тўсмайди (валюта каталоги нақши)
     */
    private void revalidateLines(JournalEntry entry, boolean revalidateClasses) {
        String home = settingsService.homeCurrency();
        for (JournalEntryLine line : entry.getLines()) {
            Account account = line.getAccount();
            if (!account.isActive()) {
                throw new PostingException(BusinessRule.BR_LED_004, line.getLineNo() + "-сатр: счёт фаол эмас: "
                        + account.getName());
            }
            if (!account.isPostable()) {
                throw new PostingException(BusinessRule.BR_LED_005, line.getLineNo() + "-сатр: гуруҳ счётига проводка мумкин эмас: " + account.getName());
            }
            validateXorAndInvariant(line.getLineNo(), line.getDebit(), line.getCredit());
            validateHomeRate(line.getLineNo(), line.getDebit(), home);
            validateHomeRate(line.getLineNo(), line.getCredit(), home);
            if (revalidateClasses) {
                validateClass(line.getLineNo(), line.getClassId());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public JournalEntry createAndPost(JournalEntryRequest request) {
        return post(createDraft(request).getId());
    }

    /** {@inheritDoc} */
    @Override
    public JournalEntry deleteDraft(UUID entryId) {
        JournalEntry entry = load(entryId);
        if (entry.getStatus() != EntryStatus.DRAFT) {
            throw new PostingException(BusinessRule.BR_LED_013,
                    "Фақат DRAFT ўчирилади: " + entry.getEntryNumber()
                    + " ҳозир " + entry.getStatus());
        }
        entryRepository.delete(entry);
        return entry;
    }

    /** {@inheritDoc} */
    @Override
    public JournalEntry reverse(UUID entryId, LocalDate reversalDate, String reason) {
        if (reversalDate == null) {
            throw new PostingException(BusinessRule.BR_LED_008, "Сторно санаси кўрсатилиши шарт");
        }
        // Сторно ҳам янги GL ҳаракати - санаси очиқ даврда бўлиши шарт.
        // Ёпиқ даврдаги ҳужжатнинг ЎЗИНИ reverse қилиш мумкин: REVERSED
        // ҳужжат балансда қолади, ёпиқ давр қолдиқлари ўзгармайди -
        // тузатиш сторно санасида акс этади (closing-date.md).
        requireOpenPeriod(reversalDate);
        JournalEntry original = load(entryId);
        if (original.getStatus() != EntryStatus.POSTED) {
            throw new PostingException(BusinessRule.BR_LED_009, "Фақат POSTED entry reverse қилинади: " + original.getEntryNumber()
                    + " ҳозир " + original.getStatus());
        }

        String description = "Сторно " + original.getEntryNumber()
                + (reason == null || reason.isBlank() ? "" : ": " + reason);
        JournalEntry storno = new JournalEntry(
                nextNumber(reversalDate), reversalDate, description,
                original.getSourceModule(), original.getSourceDocumentId());
        // INSERT'дан олдин боғланиши шарт - акс ҳолда сторно
        // ux_je_source_active index'га дубликат бўлиб йиқилади.
        storno.linkReversalOf(original);

        // Дебет ва кредит ўрин алмашади - суммалар ўз ҳолича қолади.
        // Class ҳам кўчади: сторно P&L by Class'да ҳам асл сатрни айнан
        // нейтраллаши шарт - акс ҳолда кесимда «осилиб» қоларди
        for (JournalEntryLine line : original.getLines()) {
            storno.addLine(line.getAccount(), line.getCredit(), line.getDebit(),
                    line.getContactId(), line.getWarehouseId(), line.getItemId(),
                    line.getMemo(), line.getClassId());
        }
        // Асл post'дан кейин счёт нофаол қилинган бўлиши мумкин - сторно
        // ҳам ҲАММА инвариантлардан ўтиши шарт (post() билан бир хил йўл);
        // фақат class қайта текширилмайди - тарихий тег кўчади
        revalidateLines(storno, false);
        validateBalance(storno);
        storno.markPosted(Instant.now());
        entryRepository.save(storno);

        original.markReversed(storno);
        // Техник log (Arbitr-099): сторно ҳам изли - қайси асл ҳужжат
        // қайси сторно билан бекор қилинди
        log.info("JE REVERSED: {} {} -> сторно {} home жами {}", sourceLabel(original),
                original.getEntryNumber(), storno.getEntryNumber(), homeTotal(storno));
        // Сторно алоҳида аудит ҳодисаси - JE_POSTED эмас (audit-log.md)
        eventPublisher.publishEvent(new JournalEntryReversedEvent(storno, original));
        return storno;
    }

    /** {@inheritDoc} */
    @Override
    public JournalEntry reverseBySource(String sourceModule, UUID sourceDocumentId,
                                        LocalDate reversalDate, String reason) {
        // Arbitr-080: findFirst + тартиб (энг охирги асл ёзув). Оддий
        // Optional lookup репост оқимида (REVERSED асл + POSTED репост
        // иккови reversalOf=null) NonUniqueResultException билан 500 берарди -
        // энди детерминистик равишда энг охирги (фаол POSTED) ёзувни олади.
        JournalEntry entry = entryRepository
                .findFirstBySourceModuleAndSourceDocumentIdAndReversalOfIsNullOrderByCreatedAtDescIdDesc(
                        sourceModule, sourceDocumentId)
                .orElseThrow(() -> new PostingException(BusinessRule.BR_LED_017,
                        "Манба бўйича entry топилмади: " + sourceModule + "/" + sourceDocumentId));
        return reverse(entry.getId(), reversalDate, reason);
    }

    // ---- валидация ----

    /** 1-, 2-, 5-валидациялар: структура ва Money инварианти. */
    private void validateStructure(JournalEntryRequest request) {
        if (request.entryDate() == null) {
            throw new PostingException(BusinessRule.BR_LED_014, "entry_date кўрсатилиши шарт");
        }
        // Ёпиқ даврга draft ҳам яратилмайди - у барибир post бўла олмас
        // эди, фойдаланувчи хатони эрта кўради (BR-LED-020)
        requireOpenPeriod(request.entryDate());
        if (request.lines() == null || request.lines().size() < 2) {
            throw new PostingException(BusinessRule.BR_LED_001, "Entry'да камида 2 сатр бўлиши керак");
        }
        String home = settingsService.homeCurrency();
        int no = 1;
        for (JournalEntryRequest.Line line : request.lines()) {
            validateXorAndInvariant(no, line.debit(), line.credit());
            validateHomeRate(no, line.debit(), home);
            validateHomeRate(no, line.credit(), home);
            validateCurrencyCatalog(no, line.debit());
            validateCurrencyCatalog(no, line.credit());
            validateClass(no, line.classId());
            no++;
        }
    }

    /**
     * BR-CLS-001 (class-tracking.md): танланган Class мавжуд ва фаол.
     * GL суммаларига таъсир қилмайди - фақат тег валидацияси.
     */
    private void validateClass(int lineNo, UUID classId) {
        if (classId == null) {
            return;
        }
        try {
            txnClassService.requireActive(classId);
        } catch (com.averpo.erp.shared.exception.BusinessRuleException e) {
            throw new PostingException(BusinessRule.BR_CLS_001, lineNo + "-сатр: " + e.getMessage());
        }
    }

    /**
     * Money'даги валюта коди каталогда мавжуд ва фаол бўлиши шарт -
     * Money оддий код сақлагани учун (атайлаб, QBO услуби) referential
     * integrity шу ерда таъминланади. Фақат draft яратишда: reverse
     * тарихий Money'ни кўчиради, у пайтдаги валюта кейин нофаол бўлса
     * ҳам сторно тўсилмайди.
     */
    private void validateCurrencyCatalog(int lineNo, Money money) {
        if (money == null) {
            return;
        }
        try {
            currencyService.require(money.getCurrency());
        } catch (com.averpo.erp.shared.exception.BusinessRuleException e) {
            // BR-CUR-001/002 ledger контекстида BR-LED-011 бўлиб чиқади -
            // чақирувчи учун хато манбаи проводка сатри, валюта каталоги эмас
            throw new PostingException(BusinessRule.BR_LED_011, lineNo + "-сатр: " + e.getMessage());
        }
    }

    /**
     * Period lock (BR-LED-020, QBO closing date услуби): ёпилиш санаси
     * белгиланган бўлса, унга тенг ёки ундан олдинги санага янги GL
     * ҳаракати (draft, post, сторно) ёзилмайди. NULL - қулф йўқ.
     */
    private void requireOpenPeriod(LocalDate date) {
        LocalDate closingDate = settingsService.closingDate();
        if (closingDate != null && !date.isAfter(closingDate)) {
            throw new PostingException(BusinessRule.BR_LED_020,
                    "Давр ёпилган: " + closingDate + " гача (шу кун билан бирга) "
                    + "янги GL ҳаракати тақиқ, келган сана: " + date);
        }
    }

    /**
     * Home валютадаги сатрда курс аниқ 1 бўлиши шарт - акс ҳолда
     * Money.ofBase нотўғри валюта билан чақирилган ёки фойдаланувчи
     * home сатрга курс киритиб юборган (ҳисобот бузилар эди).
     */
    private void validateHomeRate(int lineNo, Money money, String home) {
        if (money == null) {
            return;
        }
        if (home.equals(money.getCurrency())
                && money.getExchangeRate().compareTo(BigDecimal.ONE) != 0) {
            throw new PostingException(BusinessRule.BR_LED_010, lineNo + "-сатр: home валюта (" + home
                    + ") сатрида курс 1 бўлиши шарт, келди: " + money.getExchangeRate());
        }
    }

    /** 2- ва 5-валидация: XOR (сумма > 0) ва baseAmount == amount * rate. */
    private void validateXorAndInvariant(int lineNo, Money debit, Money credit) {
        boolean hasDebit = debit != null && debit.isPositive();
        boolean hasCredit = credit != null && credit.isPositive();
        if (hasDebit == hasCredit) {
            throw new PostingException(BusinessRule.BR_LED_002, lineNo + "-сатр: дебет ЁКИ кредит, "
                    + "биттаси мусбат бўлиши шарт");
        }
        Money money = hasDebit ? debit : credit;
        BigDecimal expected = money.getAmount().multiply(money.getExchangeRate());
        if (expected.subtract(money.getBaseAmount()).abs().compareTo(TOLERANCE) > 0) {
            throw new PostingException(BusinessRule.BR_LED_003, lineNo + "-сатр: baseAmount ("
                    + money.getBaseAmount() + ") amount * rate (" + expected
                    + ") га мос эмас");
        }
    }

    /** 3-валидация: счёт мавжуд, active, postable. */
    private Account resolvePostableAccount(UUID accountId) {
        if (accountId == null) {
            throw new PostingException(BusinessRule.BR_LED_016, "Сатрда счёт кўрсатилмаган");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new PostingException(BusinessRule.BR_LED_015, "Счёт топилмади: " + accountId));
        if (!account.isActive()) {
            throw new PostingException(BusinessRule.BR_LED_004, "Счёт фаол эмас: " + account.getName());
        }
        if (!account.isPostable()) {
            throw new PostingException(BusinessRule.BR_LED_005, "Гуруҳ счётига проводка мумкин эмас: " + account.getName());
        }
        return account;
    }

    /** 4-валидация: sum(debitBase) == sum(creditBase), home валютада. */
    private void validateBalance(JournalEntry entry) {
        BigDecimal debitBase = BigDecimal.ZERO;
        BigDecimal creditBase = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debitBase = debitBase.add(line.getDebit().getBaseAmount());
            }
            if (line.getCredit() != null) {
                creditBase = creditBase.add(line.getCredit().getBaseAmount());
            }
        }
        if (debitBase.compareTo(creditBase) != 0) {
            throw new PostingException(BusinessRule.BR_LED_006, "Баланс бузилган: дебет " + debitBase
                    + " != кредит " + creditBase);
        }
    }

    /** Log учун манба ёрлиғи: манба модули ёки қўлда киритилган JE учун «MANUAL». */
    private String sourceLabel(JournalEntry entry) {
        return entry.getSourceModule() == null ? "MANUAL" : entry.getSourceModule();
    }

    /** Log учун home валютадаги жами (дебет томони) - GL'га таъсир қилмайди. */
    private BigDecimal homeTotal(JournalEntry entry) {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                total = total.add(line.getDebit().getBaseAmount());
            }
        }
        return total;
    }

    /** Entry'ни сатрлари билан юклайди ёки PostingException отади. */
    private JournalEntry load(UUID entryId) {
        return entryRepository.findWithLinesById(entryId)
                .orElseThrow(() -> new PostingException(BusinessRule.BR_LED_017, "Entry топилмади: " + entryId));
    }

    /** Кетма-кет entry рақами: JE-2026-000001 (document_sequence'дан). */
    private String nextNumber(LocalDate entryDate) {
        return sequenceService.next(
                com.averpo.erp.shared.domain.DocumentType.JOURNAL_ENTRY, entryDate);
    }
}
