package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryLineRepository;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Счёт амаллари (QBO Account history / register) - битта счёт
 * қатнашган проводка сатрлари, давр филтри ва жорий қолдиқ билан.
 *
 * <p>Spec: docs/modules/ui-navigation-display.md, T1. Ledger'нинг
 * public ўқиш методи - CoA ва Trial Balance'дан drill-down шу ерга
 * келади; бошқа модуллар ҳам (масалан, келажакдаги контакт карточкаси)
 * фақат шу интерфейс орқали мурожаат қилади (ТЕМИР ҚОИДА №6).
 *
 * <p>TrialBalanceService каби POSTED билан бирга REVERSED entry'лар
 * ҳам киради: сторно жуфти иккиси ҳам GL тарихида кўринади ва нетто
 * нолга тушади. DRAFT'лар GL'да йўқ - ҳеч қачон кирмайди.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountTransactionsService {

    /** GL тарихида кўринадиган статуслар - DRAFT атайлаб йўқ. */
    private static final List<EntryStatus> GL_STATUSES =
            List.of(EntryStatus.POSTED, EntryStatus.REVERSED);

    /** GL статус номлари - native aggregate query enum'ни ўзи айлантирмайди. */
    private static final List<String> GL_STATUS_NAMES =
            GL_STATUSES.stream().map(Enum::name).toList();

    /**
     * Register'нинг битта сатри - JournalEntryLine + унинг entry
     * маълумотидан view учун етарли қисм (entity'нинг ўзи эмас:
     * шаблон lazy proxy'га тегиб кетмасин, чегара аниқ бўлсин).
     *
     * @param entryId     сатр тегишли JE id'си - қатор босилганда шу JE очилади
     * @param entryNumber ҳужжат рақами (JE-..., BILL-..., PAY-...)
     * @param entryDate   проводка санаси - register шу бўйича тартибланган
     * @param status      POSTED ёки REVERSED - сторно қаторни белгилаш учун
     * @param description сатр memo'си, у бўш бўлса entry тавсифи (QBO
     *                    register'даги Memo устуни услуби)
     * @param contactId   контрагент dimension ёки {@code null}; ном ечиш
     *                    ledger'да ЭМАС - ledger contact модулига боғлана
     *                    олмайди, номни экран қатлами ҳал қилади
     * @param debit       дебет суммаси (ҳужжат валютасида, Money) ёки {@code null}
     * @param credit      кредит суммаси ёки {@code null}
     * @param balance     шу сатрдан КЕЙИНГИ жорий қолдиқ - home валютада,
     *                    signed (мусбат - дебет); ишора нормализацияси
     *                    (пассивни кредит-мусбат кўрсатиш) чақирувчида
     */
    public record Row(UUID entryId, String entryNumber, LocalDate entryDate,
                      EntryStatus status, String description, UUID contactId,
                      Money debit, Money credit, BigDecimal balance) { }

    /**
     * Битта счётнинг давр кесимидаги тўлиқ register'и.
     *
     * @param account счётнинг ўзи - экран сарлавҳаси учун, чақирувчи
     *                қайта юкламасин
     * @param opening давр бошидаги қолдиқ (home, signed) - from'дан
     *                олдинги БАРЧА GL сатрлар йиғиндиси
     * @param rows    давр ичидаги сатрлар, сана тартибида
     * @param closing давр охиридаги қолдиқ: opening + Σ(дебет - кредит);
     *                rows бўш бўлса opening'га тенг
     */
    public record Register(Account account, LocalDate from, LocalDate to,
                           BigDecimal opening, List<Row> rows, BigDecimal closing) { }

    /** Счёт мавжудлигини текшириш учун - ўз модулимиз репозиторийси. */
    private final AccountRepository accountRepository;

    /** Register сатрлари ва очилиш қолдиғи query'лари. */
    private final JournalEntryLineRepository lineRepository;

    /**
     * Контакт номларини хом SQL name-map билан ечиш учун (Arbitr-044).
     * Ledger contact модулига боғлана олмайди (ТЕМИР ҚОИДА №6 - «ledger
     * ҳеч кимга боғлиқ эмас»): ContactService import'и ўрнига {@code contact}
     * жадвалини бевосита ўқиймиз (BalanceSheet/LedgerDashboard прецеденти -
     * ledger ҳисоботлари бошқа модул жадвалларини import'сиз, SQL билан ўқийди).
     */
    private final JdbcClient jdbc;

    /**
     * Register'нинг битта саҳифаси (ARBITR-105б): {@link Register}
     * семантикаси ўзгармаган (opening/closing - ДАВР қийматлари, ҳар
     * саҳифада бир хил), {@code rows} эса фақат жорий саҳифа сатрлари.
     * {@code page} - саҳифалаш метадатаси (shared/pagination.jte учун);
     * унинг content'и {@code register.rows()} билан айнан бир рўйхат.
     */
    public record PagedRegister(Register register, Page<Row> page) { }

    /**
     * [from, to] даври (иккала чегара инклюзив) учун счёт register'ини
     * қуради. Жорий қолдиқ давр бошидаги қолдиқдан бошлаб сатрма-сатр
     * ҳисобланади - шунинг учун ҳар сатрдаги қолдиқ счётнинг ўша
     * пайтдаги ҲАҚИҚИЙ қолдиғи (фақат давр айланмаси эмас).
     *
     * <p>Саҳифаланган вариантнинг «ҳаммаси бир саҳифада» делегати -
     * битта код йўли, хатти-ҳаракат айнан аввалгидек.
     *
     * @throws NotFoundException счёт топилмаса - public метод ўз
     *         параметрини ўзи текширади, чақирувчига ишониб қолмайди
     */
    public Register register(UUID accountId, LocalDate from, LocalDate to) {
        return registerPage(accountId, from, to, 0, Integer.MAX_VALUE).register();
    }

    /**
     * Register'нинг {@code page}-саҳифаси (ARBITR-105б). Жорий қолдиқ
     * узлуксизлиги учун саҳифа бошидаги қолдиқ «саҳифагача йиғинди»
     * aggregate'идан олинади ({@code sumBaseFirstRegisterRows} - олдинги
     * саҳифалар сатрлари Java'га юкланмайди): кейинги саҳифа биринчи
     * сатри олдинги саҳифа охирги сатрининг айнан давоми бўлади.
     *
     * <p>Давр closing'и охирги саҳифада running'нинг ўзи (аггрегатсиз);
     * ўрта саҳифаларда алоҳида давр айланма aggregate'и билан - ҳар
     * саҳифада бир хил ДАВР қиймати кўрсатилади.
     *
     * @param page 0-асосли саҳифа рақами (манфий - 0 га қисилади)
     * @param size саҳифа ҳажми (controller PageSizeResolver'дан беради)
     * @throws NotFoundException счёт топилмаса
     */
    public PagedRegister registerPage(UUID accountId, LocalDate from, LocalDate to,
                                      int page, int size) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Счёт топилмади: " + accountId));

        BigDecimal opening = zeroIfNull(lineRepository.sumBaseBefore(accountId, GL_STATUSES, from));

        var pageable = PageRequest.of(Math.max(0, page), size);
        Page<JournalEntryLine> lines =
                lineRepository.findRegisterLines(accountId, GL_STATUSES, from, to, pageable);

        // Саҳифагача йиғинди - 1-саҳифада нол, aggregate умуман юрмайди
        BigDecimal beforePage = pageable.getOffset() == 0 ? BigDecimal.ZERO
                : zeroIfNull(lineRepository.sumBaseFirstRegisterRows(
                        accountId, GL_STATUS_NAMES, from, to, pageable.getOffset()));

        List<Row> rows = new ArrayList<>(lines.getNumberOfElements());
        BigDecimal running = opening.add(beforePage);
        for (JournalEntryLine line : lines) {
            running = running.add(baseOrZero(line.getDebit()))
                    .subtract(baseOrZero(line.getCredit()));
            JournalEntry entry = line.getEntry();
            rows.add(new Row(entry.getId(), entry.getEntryNumber(), entry.getEntryDate(),
                    entry.getStatus(),
                    line.getMemo() != null ? line.getMemo() : entry.getDescription(),
                    line.getContactId(), line.getDebit(), line.getCredit(), running));
        }

        // Охирги саҳифада running = давр closing'и; ўртада алоҳида aggregate
        BigDecimal closing = lines.isLast() ? running
                : opening.add(zeroIfNull(
                        lineRepository.sumBaseBetween(accountId, GL_STATUSES, from, to)));
        Register register = new Register(account, from, to, opening, List.copyOf(rows), closing);
        return new PagedRegister(register,
                new PageImpl<>(register.rows(), pageable, lines.getTotalElements()));
    }

    /** Aggregate null қайтарса (кесим бўш) нолга айлантиради. */
    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Берилган контакт id'лари учун {@code id → displayName} харитаси -
     * register экрани контрагент устуни учун (Arbitr-044). ФАҚАТ саҳифада
     * кўринган сатрлардаги id'лар узатилади (IN query, бутун каталог эмас);
     * бўш рўйхатда SQL умуман юрмайди. Ном {@code contact} жадвалидан хом
     * SQL билан - ledger contact модулига боғланмайди (ТЕМИР ҚОИДА №6).
     * Топилмаган id харитада бўлмайди - чақирувчи {@code getOrDefault} билан ўқийди.
     */
    public Map<UUID, String> contactNames(Collection<UUID> contactIds) {
        Map<UUID, String> names = new HashMap<>();
        if (contactIds.isEmpty()) {
            return names;
        }
        jdbc.sql("SELECT id, display_name FROM contact WHERE id IN (:ids)")
                .param("ids", contactIds)
                .query(rs -> { names.put(rs.getObject("id", UUID.class),
                        rs.getString("display_name")); });
        return names;
    }

    /** XOR сатрда ишлатилмаган томон null - қолдиқ ҳисобида нол. */
    private static BigDecimal baseOrZero(Money money) {
        return money == null ? BigDecimal.ZERO : money.getBaseAmount();
    }

    /**
     * Reconciliation номзоди - GL сатрининг банк солиштируви учун
     * керакли қисми (bank модули учун, docs/modules/banking.md).
     *
     * @param lineId       GL сатри id'си - match айнан шунга боғланади
     * @param entryId      сатр тегишли JE id'си (қатор босилганда очилади)
     * @param entryNumber  ҳужжат рақами
     * @param entryDate    проводка санаси
     * @param status       POSTED ёки REVERSED (сторно жуфти ҳам киради)
     * @param description  сатр memo'си, бўш бўлса entry тавсифи
     * @param signedAmount сумма СЧЁТ ВАЛЮТАСИДА, ишорали (Dt +, Cr -)
     */
    public record ReconcilableLine(UUID lineId, UUID entryId, String entryNumber,
                                   LocalDate entryDate, EntryStatus status,
                                   String description, BigDecimal signedAmount) { }

    /**
     * Счётнинг {@code toDate}'гача (инклюзив) бўлган БАРЧА GL сатрлари
     * - банк reconciliation номзодлари. POSTED билан бирга REVERSED
     * ҳам киради: кўчирмада сторно жуфтининг иккиси ҳам туради ва
     * белгиланганда неттоси нолга тушади. DRAFT ҳеч қачон кирмайди.
     *
     * <p>Сумма счёт валютасида: счёт home'да (currency бўш) -
     * baseAmount; чет валютали счётда валюта мос сатрда amount, мос
     * бўлмаган (қўлда JE) сатрда baseAmount тахминий олинади.
     */
    public List<ReconcilableLine> reconcilableLines(UUID accountId, LocalDate toDate) {
        Objects.requireNonNull(toDate, "toDate");
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Счёт топилмади: " + accountId));
        String accountCurrency = account.getCurrency() == null
                ? null : account.getCurrency().getCode();

        List<JournalEntryLine> lines = lineRepository.findRegisterLines(
                accountId, GL_STATUSES, LocalDate.of(1970, 1, 1), toDate);
        List<ReconcilableLine> result = new ArrayList<>(lines.size());
        for (JournalEntryLine line : lines) {
            JournalEntry entry = line.getEntry();
            BigDecimal amount = signedAccountAmount(line, accountCurrency);
            result.add(new ReconcilableLine(line.getId(), entry.getId(),
                    entry.getEntryNumber(), entry.getEntryDate(), entry.getStatus(),
                    line.getMemo() != null ? line.getMemo() : entry.getDescription(),
                    amount));
        }
        return List.copyOf(result);
    }

    /**
     * Битта GL сатрини reconciliation номзоди сифатида қайтаради -
     * банкдаги toggle (белгилаш/ечиш) йўли учун.
     *
     * <p>{@link #reconcilableLines} бутун тарихни ўқийди; ҳар
     * белгилашда уни чақириш банк тарихи ўсганда сессияни
     * секинлаштиради (Beruniy-perf2). Бу метод фақат сўралган сатрни
     * текширади: шу счётники, GL статусда (POSTED/REVERSED) ва санаси
     * {@code toDate}'дан ошмаган бўлиши шарт - худди рўйхат методи
     * қайтарадиган мезонлар, лекин биттагина ўқиш билан.
     *
     * @return мезонларга мос келса тўлдирилган номзод, акс ҳолда бўш
     */
    public Optional<ReconcilableLine> reconcilableLine(UUID accountId, UUID lineId,
                                                       LocalDate toDate) {
        Objects.requireNonNull(toDate, "toDate");
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Счёт топилмади: " + accountId));
        String accountCurrency = account.getCurrency() == null
                ? null : account.getCurrency().getCode();
        return lineRepository.findById(lineId)
                .filter(line -> line.getAccount().getId().equals(accountId))
                .filter(line -> GL_STATUSES.contains(line.getEntry().getStatus()))
                .filter(line -> !line.getEntry().getEntryDate().isAfter(toDate))
                .map(line -> new ReconcilableLine(line.getId(),
                        line.getEntry().getId(), line.getEntry().getEntryNumber(),
                        line.getEntry().getEntryDate(), line.getEntry().getStatus(),
                        line.getMemo() != null ? line.getMemo()
                                : line.getEntry().getDescription(),
                        signedAccountAmount(line, accountCurrency)));
    }

    /**
     * Сатр суммаси счёт валютасида, ишорали (Dt +, Cr -): home счётда
     * baseAmount, чет валютали счётда валюта мос бўлса amount.
     */
    private static BigDecimal signedAccountAmount(JournalEntryLine line,
                                                  String accountCurrency) {
        Money debit = line.getDebit();
        Money credit = line.getCredit();
        Money money = debit != null ? debit : credit;
        BigDecimal amount;
        if (accountCurrency == null || !accountCurrency.equals(money.getCurrency())) {
            amount = money.getBaseAmount();
        } else {
            amount = money.getAmount();
        }
        return debit != null ? amount : amount.negate();
    }
}
