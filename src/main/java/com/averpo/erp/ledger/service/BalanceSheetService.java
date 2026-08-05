package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Balance Sheet - QBO тузилмасида (docs/modules/reports.md).
 *
 * <p>Trial Balance услуби: JdbcClient SQL агрегат (journal_entry_line
 * энг катта жадвал), POSTED+REVERSED (сторно жуфти неттоси нолга
 * тушади), барча суммалар home валютада. Ишора: актив Dt-Cr,
 * мажбурият/капитал Cr-Dt - мусбат сон нормал қолдиқ.
 *
 * <p>Тақсимланмаган фойда / Соф фойда QBO услубида ёпиш проводкасисиз
 * «виртуал» ҳисобланади: жорий молия йили (CompanySettings'даги
 * бошланиш ойидан) P&L неттоси - Соф фойда сатри, ундан олдингиси
 * RETAINED_EARNINGS счёти қолдиғига қўшилади.
 *
 * <p><b>Стандарт - IAS 1 «Молиявий ҳисоботлар тақдимоти»</b>: ҳисобот
 * актив / мажбурият / капитал бўлимларида тақдим этилади ва
 * {@code актив = мажбурият + капитал} тенглиги сақланади - бу ерда у
 * ҳисоблаб чиқарилиб, тест билан текширилади. Жорий йил фойдасини
 * тақсимланмаган фойдадан молия йили бўйича ажратиш ҳам IAS 1
 * тақдимотига мос (капитал ҳаракати кўринарли бўлади).
 *
 * <p>Балансланиш тасодиф эмас, тузилмавий: ledger'нинг ўзи home
 * валютада {@code sum(debitBase) = sum(creditBase)} инварианти билан
 * ёзилади (темир қоида №4), ҳисобот эса фақат ўша ledger'дан ўқийди -
 * ҳеч бир модул ўз қолдиғини сақламайди.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BalanceSheetService {

    /**
     * Битта счёт сатри. Суммаси home валютада, ишораси нормализацияланган
     * (актив Dt-Cr, мажбурият/капитал Cr-Dt).
     *
     * @param accountId drill-down («Счёт амаллари») учун счёт id'си
     * @param name      счёт номи (unique идентификатор)
     * @param code      ихтиёрий счёт рақами, бўлмаса null
     * @param amount    нормализацияланган қолдиқ
     */
    public record Row(UUID accountId, String name, String code, BigDecimal amount) { }

    /**
     * Тур даражасидаги гуруҳ (QBO: «Bank Accounts», «Accounts
     * Receivable»...) - жорий актив/мажбурият бўлимлари ичида.
     *
     * @param type     гуруҳ тури; detail type бўйича ажратилган псевдо
     *                 гуруҳда (масалан ТМЗ - Komil-011) null - тестлар
     *                 тур бўйича излаганда псевдо гуруҳ аралашмайди
     * @param titleKey сарлавҳа i18n калити - тур гуруҳида type.titleKey(),
     *                 псевдо гуруҳда detail type калити
     * @param rows     ноль бўлмаган қолдиқли счёт сатрлари
     * @param total    гуруҳ жамиси
     */
    public record Group(AccountType type, String titleKey, List<Row> rows, BigDecimal total) { }

    /**
     * Тайёр ҳисобот - шаблон тўғридан-тўғри render қилади.
     *
     * @param asOf ҳисобот санаси («шу санага»)
     * @param currentAssetGroups жорий активлар: BANK → AR → ТМЗ → OTHER_CURRENT_ASSET тартибида
     * @param vendorPrepayments таъминотчиларга тақсимланмаган аванслар (home'да) -
     *                          AP дебет қолдиғидан prepayment активга КЎРСАТИШДА
     *                          reclass қилинган сумма (Komil-005, IAS 1.32); GL'га тегилмайди
     * @param employeeAdvances ходимларга берилган аванслар (home'да) - PAYROLL_CLEARING
     *                         ходим кесимидаги нетто дебет қолдиқлардан активга
     *                         КЎРСАТИШДА reclass (Komil-020, IAS 1.32); GL'га тегилмайди
     * @param totalCurrentAssets жами жорий активлар (vendorPrepayments ва
     *                           employeeAdvances билан)
     * @param fixedAssets асосий воситалар сатрлари (FIXED_ASSET)
     * @param totalFixedAssets жами асосий воситалар
     * @param goodwill гудвилл сатрлари (GOODWILL detail) - IAS 1.54(c) алоҳида модда (Komil-012)
     * @param totalGoodwill жами гудвилл
     * @param intangibleAssets номоддий актив сатрлари (INTANGIBLE_ASSETS detail) - IAS 1.54(c)
     * @param totalIntangibleAssets жами номоддий активлар
     * @param otherAssets бошқа активлар сатрлари (OTHER_ASSET, гудвилл/номоддийсиз)
     * @param totalOtherAssets жами бошқа активлар
     * @param totalAssets жами активлар
     * @param currentLiabilityGroups жорий мажбуриятлар: AP → CREDIT_CARD → OTHER_CURRENT_LIABILITY
     * @param customerAdvances мижозлардан тақсимланмаган аванслар (home'да) -
     *                         AR кредит қолдиғидан мажбуриятга КЎРСАТИШДА reclass
     *                         қилинган сумма (Komil-005, IFRS 15.106); GL'га тегилмайди
     * @param totalCurrentLiabilities жами жорий мажбуриятлар (customerAdvances билан)
     * @param longTermLiabilities узоқ муддатли мажбурият сатрлари
     * @param totalLongTermLiabilities жами узоқ муддатли мажбуриятлар
     * @param totalLiabilities жами мажбуриятлар
     * @param equityRows капитал счёт сатрлари (RETAINED_EARNINGS'дан ташқари)
     * @param retainedEarnings Тақсимланмаган фойда: RE счёти қолдиғи + олдинги молия йиллари соф фойдаси
     * @param netIncome жорий молия йили (fyStart..asOf) соф фойдаси
     * @param totalEquity жами капитал (equityRows + RE + NI)
     * @param totalLiabilitiesAndEquity жами мажбурият ва капитал
     * @param balanced Жами актив == Жами мажбурият + капитал текшируви -
     *                 false бўлса GL бузилган (бўлмаслиги керак), шаблон
     *                 огоҳлантириш кўрсатади
     */
    public record Report(LocalDate asOf,
                         List<Group> currentAssetGroups, BigDecimal vendorPrepayments,
                         BigDecimal employeeAdvances,
                         BigDecimal totalCurrentAssets,
                         List<Row> fixedAssets, BigDecimal totalFixedAssets,
                         List<Row> goodwill, BigDecimal totalGoodwill,
                         List<Row> intangibleAssets, BigDecimal totalIntangibleAssets,
                         List<Row> otherAssets, BigDecimal totalOtherAssets,
                         BigDecimal totalAssets,
                         List<Group> currentLiabilityGroups, BigDecimal customerAdvances,
                         BigDecimal totalCurrentLiabilities,
                         List<Row> longTermLiabilities, BigDecimal totalLongTermLiabilities,
                         BigDecimal totalLiabilities,
                         List<Row> equityRows, BigDecimal retainedEarnings,
                         BigDecimal netIncome, BigDecimal totalEquity,
                         BigDecimal totalLiabilitiesAndEquity, boolean balanced) { }

    /** Жорий активлар бўлимидаги гуруҳ тартиби (QBO). */
    private static final List<AccountType> CURRENT_ASSET_ORDER = List.of(
            AccountType.BANK, AccountType.ACCOUNTS_RECEIVABLE, AccountType.OTHER_CURRENT_ASSET);

    /** Жорий мажбуриятлар бўлимидаги гуруҳ тартиби (QBO). */
    private static final List<AccountType> CURRENT_LIABILITY_ORDER = List.of(
            AccountType.ACCOUNTS_PAYABLE, AccountType.CREDIT_CARD,
            AccountType.OTHER_CURRENT_LIABILITY);

    /** SQL агрегат учун JdbcClient - Hibernate'ни четлаб ўтади. */
    private final JdbcClient jdbc;

    /** Молия йили бошланиш ойи учун созламалар (fiscalYearStart). */
    private final CompanySettingsService settingsService;

    /** Балансни :asOf санасига қуради (шу кун билан бирга). */
    public Report build(LocalDate asOf) {
        LocalDate fyStart = settingsService.get().fiscalYearStart(asOf);

        // 1. Balance-sheet счётларининг хом қолдиқлари (Dt-Cr) тур кесимида
        Map<AccountType, List<RawRow>> byType = new EnumMap<>(AccountType.class);
        BigDecimal[] retainedEarningsAccount = {BigDecimal.ZERO};
        // IAS 1.54 алоҳида моддалар (Komil-011/012): detail type бўйича ўз
        // рўйхатига ажратилади - GL ва тур ўзгармайди, фақат кўрсатиш
        List<RawRow> inventoryRaw = new ArrayList<>();
        List<RawRow> goodwillRaw = new ArrayList<>();
        List<RawRow> intangibleRaw = new ArrayList<>();
        // Аванс reclass'и учун AR/AP тизим счёти id'си (тўловлар айнан шу
        // detail'даги счётга проводка қилинади - Komil-005)
        UUID[] arAccount = {null};
        UUID[] apAccount = {null};
        // Ходим аванси reclass'и учун PAYROLL_CLEARING счёти (Komil-020)
        UUID[] payrollClearingAccount = {null};
        jdbc.sql("""
                SELECT a.id, a.name, a.code, a.type, a.classification, a.detail_type,
                       COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                           - COALESCE(l.credit_base_amount, 0)), 0) AS bal
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date <= :asOf
                  AND a.classification IN ('ASSET', 'LIABILITY', 'EQUITY')
                GROUP BY a.id, a.name, a.code, a.type, a.classification, a.detail_type
                """)
                .param("asOf", asOf)
                .query(rs -> {
                    BigDecimal bal = rs.getBigDecimal("bal");
                    String detailType = rs.getString("detail_type");
                    AccountType type = safeType(rs.getString("type"),
                            rs.getString("classification"));
                    // RE счёти алоҳида йиғилади - QBO услубида олдинги йиллар
                    // фойдаси билан битта сатрга қўшилади (Cr-Dt ишорада)
                    if (AccountDetailType.RETAINED_EARNINGS.name().equals(detailType)) {
                        retainedEarningsAccount[0] =
                                retainedEarningsAccount[0].add(bal.negate());
                        return;
                    }
                    RawRow raw = new RawRow(rs.getObject("id", UUID.class),
                            rs.getString("name"), rs.getString("code"), bal);
                    if (AccountDetailType.INVENTORY.name().equals(detailType)) {
                        inventoryRaw.add(raw);
                    } else if (AccountDetailType.GOODWILL.name().equals(detailType)) {
                        goodwillRaw.add(raw);
                    } else if (AccountDetailType.INTANGIBLE_ASSETS.name().equals(detailType)) {
                        intangibleRaw.add(raw);
                    } else {
                        if (AccountDetailType.ACCOUNTS_RECEIVABLE.name().equals(detailType)
                                && arAccount[0] == null) {
                            arAccount[0] = raw.accountId();
                        }
                        if (AccountDetailType.ACCOUNTS_PAYABLE.name().equals(detailType)
                                && apAccount[0] == null) {
                            apAccount[0] = raw.accountId();
                        }
                        if (AccountDetailType.PAYROLL_CLEARING.name().equals(detailType)
                                && payrollClearingAccount[0] == null) {
                            payrollClearingAccount[0] = raw.accountId();
                        }
                        byType.computeIfAbsent(type, t -> new ArrayList<>()).add(raw);
                    }
                });

        // 1а. Тақсимланмаган аванслар (Komil-005): мижоз аванси AR кредит
        // қолдиғидан мажбуриятга, таъминотчи аванси AP дебет қолдиғидан
        // prepayment активга фақат КЎРСАТИШДА reclass қилинади - GL'га
        // тегилмайди (IAS 1.32 / IFRS 15.106). Сумма GL'нинг ЎЗИДАН,
        // contact кесимида as-of (Komil-015/016): домен жадвали жорий
        // ҳолати эмас - future reverse ва unapplied CM/VC автоматик тўғри.
        BigDecimal customerAdvances = reclass(byType, AccountType.ACCOUNTS_RECEIVABLE,
                arAccount[0], customerAdvancesAsOf(asOf), true);
        BigDecimal vendorPrepayments = reclass(byType, AccountType.ACCOUNTS_PAYABLE,
                apAccount[0], vendorPrepaymentsAsOf(asOf), false);
        // 1б. Ходимларга берилган аванслар (Komil-020, IAS 1.32): аванс
        // тўлангач run ҳали POSTED бўлмаса PAYROLL_CLEARING ходим кесимида
        // нетто ДЕБЕТ қолади - мажбурият сатри камайиб (ҳатто манфий бўлиб)
        // кўринарди. AP prepayment кўзгуси: сумма фақат КЎРСАТИШДА
        // активга reclass, кредит қолдиқлар мажбуриятда қолади.
        BigDecimal employeeAdvances = reclass(byType, AccountType.OTHER_CURRENT_LIABILITY,
                payrollClearingAccount[0], employeeAdvancesAsOf(asOf), false);

        // 2. Соф фойда bucket'лари: жорий молия йили ва ундан олдингиси
        BigDecimal[] netIncomeBuckets = {BigDecimal.ZERO, BigDecimal.ZERO}; // [current, prior]
        jdbc.sql("""
                SELECT (je.entry_date >= :fyStart) AS current_fy,
                       COALESCE(SUM(COALESCE(l.credit_base_amount, 0)
                           - COALESCE(l.debit_base_amount, 0)), 0) AS net
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date <= :asOf
                  AND a.classification IN ('REVENUE', 'EXPENSE')
                GROUP BY 1
                """)
                .param("asOf", asOf)
                .param("fyStart", fyStart)
                .query(rs -> {
                    netIncomeBuckets[rs.getBoolean("current_fy") ? 0 : 1] =
                            rs.getBigDecimal("net");
                });

        // 3. Бўлимларни йиғиш: актив Dt-мусбат, мажбурият/капитал Cr-мусбат
        List<Group> currentAssets = groups(byType, CURRENT_ASSET_ORDER, false);
        // ТМЗ псевдо гуруҳи AR'дан кейин, OTHER_CURRENT_ASSET'дан олдин
        // (QBO'да ҳам Inventory алоҳида кўринади; IAS 1.54(g))
        List<Row> inventoryRows = normalize(inventoryRaw, false);
        if (!inventoryRows.isEmpty()) {
            currentAssets.add(insertionIndex(currentAssets),
                    new Group(null, AccountDetailType.INVENTORY.titleKey(),
                            inventoryRows, sum(inventoryRows)));
        }
        List<Row> fixedAssets = rows(byType, AccountType.FIXED_ASSET, false);
        List<Row> goodwill = normalize(goodwillRaw, false);
        List<Row> intangibleAssets = normalize(intangibleRaw, false);
        List<Row> otherAssets = rows(byType, AccountType.OTHER_ASSET, false);
        BigDecimal totalCurrentAssets = totalOf(currentAssets).add(vendorPrepayments)
                .add(employeeAdvances);
        BigDecimal totalFixedAssets = sum(fixedAssets);
        BigDecimal totalGoodwill = sum(goodwill);
        BigDecimal totalIntangibleAssets = sum(intangibleAssets);
        BigDecimal totalOtherAssets = sum(otherAssets);
        BigDecimal totalAssets = totalCurrentAssets.add(totalFixedAssets)
                .add(totalGoodwill).add(totalIntangibleAssets).add(totalOtherAssets);

        List<Group> currentLiabilities = groups(byType, CURRENT_LIABILITY_ORDER, true);
        List<Row> longTermLiabilities = rows(byType, AccountType.LONG_TERM_LIABILITY, true);
        BigDecimal totalCurrentLiabilities = totalOf(currentLiabilities).add(customerAdvances);
        BigDecimal totalLongTermLiabilities = sum(longTermLiabilities);
        BigDecimal totalLiabilities = totalCurrentLiabilities.add(totalLongTermLiabilities);

        List<Row> equityRows = rows(byType, AccountType.EQUITY, true);
        BigDecimal retainedEarnings = retainedEarningsAccount[0].add(netIncomeBuckets[1]);
        BigDecimal netIncome = netIncomeBuckets[0];
        BigDecimal totalEquity = sum(equityRows).add(retainedEarnings).add(netIncome);
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);

        return new Report(asOf,
                currentAssets, vendorPrepayments, employeeAdvances, totalCurrentAssets,
                fixedAssets, totalFixedAssets,
                goodwill, totalGoodwill,
                intangibleAssets, totalIntangibleAssets,
                otherAssets, totalOtherAssets,
                totalAssets,
                currentLiabilities, customerAdvances, totalCurrentLiabilities,
                longTermLiabilities, totalLongTermLiabilities,
                totalLiabilities,
                equityRows, retainedEarnings, netIncome, totalEquity,
                totalLiabilitiesAndEquity,
                totalAssets.compareTo(totalLiabilitiesAndEquity) == 0);
    }

    /** SQL'дан келган хом сатр - ишораси ҳали нормализацияланмаган (Dt-Cr). */
    private record RawRow(UUID accountId, String name, String code, BigDecimal balance) { }

    /**
     * Нотаниш тур (эски/кўчирилган база) 500 бермасин: classification'и
     * бўйича catch-all турга тушади - сумма йўқолмайди, фақат
     * жойлашуви умумийроқ бўлади.
     */
    private static AccountType safeType(String typeName, String classification) {
        try {
            return AccountType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return switch (classification) {
                case "LIABILITY" -> AccountType.OTHER_CURRENT_LIABILITY;
                case "EQUITY" -> AccountType.EQUITY;
                default -> AccountType.OTHER_CURRENT_ASSET;
            };
        }
    }

    /** Берилган тартибдаги турлардан бўш бўлмаган гуруҳларни йиғади. */
    private static List<Group> groups(Map<AccountType, List<RawRow>> byType,
                                      List<AccountType> order, boolean creditNormal) {
        List<Group> result = new ArrayList<>();
        for (AccountType type : order) {
            List<Row> rows = rows(byType, type, creditNormal);
            if (!rows.isEmpty()) {
                result.add(new Group(type, type.titleKey(), rows, sum(rows)));
            }
        }
        return result;
    }

    /**
     * ТМЗ псевдо гуруҳининг жойи: OTHER_CURRENT_ASSET гуруҳидан олдин
     * (QBO тартиби: Bank → AR → Inventory → Other Current Assets);
     * у бўлмаса охирига.
     */
    private static int insertionIndex(List<Group> currentAssets) {
        for (int i = 0; i < currentAssets.size(); i++) {
            if (currentAssets.get(i).type() == AccountType.OTHER_CURRENT_ASSET) {
                return i;
            }
        }
        return currentAssets.size();
    }

    /** Бир турдаги сатрлар - normalize'нинг тур бўйича қисқартмаси. */
    private static List<Row> rows(Map<AccountType, List<RawRow>> byType,
                                  AccountType type, boolean creditNormal) {
        return normalize(byType.getOrDefault(type, List.of()), creditNormal);
    }

    /**
     * Хом сатрларни кўрсатишга тайёрлайди: ишора нормализацияси
     * (creditNormal - Cr-Dt), ноль қолдиқлилар яширилади (QBO default),
     * ном бўйича тартиб.
     */
    private static List<Row> normalize(List<RawRow> raw, boolean creditNormal) {
        return raw.stream()
                .map(r -> new Row(r.accountId(), r.name(), r.code(),
                        creditNormal ? r.balance().negate() : r.balance()))
                .filter(r -> r.amount().signum() != 0)
                .sorted(Comparator.comparing(Row::name))
                .toList();
    }

    /**
     * Мижозлардан тақсимланмаган аванслар home'да - GL'нинг ЎЗИДАН
     * (Komil-015/016 тузатиши): AR детал счётларида ҳар мижоз (contact)
     * кесимида нетто КРЕДИТ қолдиғи (Cr &gt; Dt) = олинган аванс, барча
     * бундай қолдиқлар йиғиндиси. Ишора base'да (home) - JE'нинг ўзи
     * home валютада балансланган, курс allocation пайтида ёзилган.
     *
     * <p>Нега домен жадвали (invoice_payment.unallocated) ЭМАС: у жорий
     * ҳолат - as-of тарихни билмайди. GL as-of ҳар доим тўғри - future
     * reverse автоматик ҳисобга киради (тўлов REVERSED бўлса AR сатри
     * ҳам сторноланади, аванс йўқолади), unapplied CreditMemo/қайтариш
     * қолдиғи ҳам киради (улар ҳам AR contact кесимида кредит қолдиқ).
     * contact'сиз (мануал) AR сатрлари авансга кирмайди - аванс ҳар доим
     * маълум мижозга боғлиқ.
     */
    private BigDecimal customerAdvancesAsOf(LocalDate asOf) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(t.bal), 0) FROM (
                    SELECT COALESCE(SUM(COALESCE(l.credit_base_amount, 0)
                        - COALESCE(l.debit_base_amount, 0)), 0) AS bal
                    FROM journal_entry_line l
                    JOIN journal_entry je ON je.id = l.entry_id
                    JOIN account a ON a.id = l.account_id
                    WHERE je.status IN ('POSTED', 'REVERSED')
                      AND je.entry_date <= :asOf
                      AND a.detail_type = 'ACCOUNTS_RECEIVABLE'
                      AND l.contact_id IS NOT NULL
                    GROUP BY l.contact_id
                    HAVING COALESCE(SUM(COALESCE(l.credit_base_amount, 0)
                        - COALESCE(l.debit_base_amount, 0)), 0) > 0
                ) t
                """)
                .param("asOf", asOf)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Таъминотчиларга тақсимланмаган аванслар home'да - GL'нинг ЎЗИДАН
     * (тузилма customerAdvancesAsOf кўзгуси): AP детал счётларида ҳар
     * таъминотчи кесимида нетто ДЕБЕТ қолдиғи (Dt &gt; Cr) = берилган
     * аванс. Future reverse ва unapplied VendorCredit автоматик киради.
     */
    private BigDecimal vendorPrepaymentsAsOf(LocalDate asOf) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(t.bal), 0) FROM (
                    SELECT COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                        - COALESCE(l.credit_base_amount, 0)), 0) AS bal
                    FROM journal_entry_line l
                    JOIN journal_entry je ON je.id = l.entry_id
                    JOIN account a ON a.id = l.account_id
                    WHERE je.status IN ('POSTED', 'REVERSED')
                      AND je.entry_date <= :asOf
                      AND a.detail_type = 'ACCOUNTS_PAYABLE'
                      AND l.contact_id IS NOT NULL
                    GROUP BY l.contact_id
                    HAVING COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                        - COALESCE(l.credit_base_amount, 0)), 0) > 0
                ) t
                """)
                .param("asOf", asOf)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Ходимларга берилган аванслар home'да - GL'нинг ЎЗИДАН (тузилма
     * vendorPrepaymentsAsOf кўзгуси): PAYROLL_CLEARING детал счётида ҳар
     * ходим (contact) кесимида нетто ДЕБЕТ қолдиғи (Dt &gt; Cr) = run
     * ҳали ёпмаган аванс. Future reverse автоматик киради; кредит
     * қолдиқли ходимлар (ҳисобланган, тўланмаган) мажбуриятда қолади.
     */
    private BigDecimal employeeAdvancesAsOf(LocalDate asOf) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(t.bal), 0) FROM (
                    SELECT COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                        - COALESCE(l.credit_base_amount, 0)), 0) AS bal
                    FROM journal_entry_line l
                    JOIN journal_entry je ON je.id = l.entry_id
                    JOIN account a ON a.id = l.account_id
                    WHERE je.status IN ('POSTED', 'REVERSED')
                      AND je.entry_date <= :asOf
                      AND a.detail_type = 'PAYROLL_CLEARING'
                      AND l.contact_id IS NOT NULL
                    GROUP BY l.contact_id
                    HAVING COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                        - COALESCE(l.credit_base_amount, 0)), 0) > 0
                ) t
                """)
                .param("asOf", asOf)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Аванс reclass'и (фақат кўрсатиш): тизим счёти хом қолдиғига delta
     * қўшиб авансни «ажратади» - AR'да +advance (кредит қисми чиқарилиб
     * gross AR қолади), AP'да -advance (Dt-Cr хомда дебет қисми чиқарилиб
     * gross AP қолади). Счёт сатри топилмаса 0 қайтади - виртуал сатр ҳам
     * кўрсатилмайди, БАЛАНС ТЕНГЛАМАСИ ҳеч қачон бузилмайди.
     *
     * @param debitAdjust true - хом қолдиққа қўшиш (AR), false - айириш (AP)
     * @return амалда reclass қилинган сумма (0 ёки advance)
     */
    private static BigDecimal reclass(Map<AccountType, List<RawRow>> byType,
                                      AccountType type, UUID accountId,
                                      BigDecimal advance, boolean debitAdjust) {
        if (advance.signum() == 0 || accountId == null) {
            return BigDecimal.ZERO;
        }
        List<RawRow> rows = byType.get(type);
        if (rows == null) {
            return BigDecimal.ZERO;
        }
        for (int i = 0; i < rows.size(); i++) {
            RawRow row = rows.get(i);
            if (row.accountId().equals(accountId)) {
                BigDecimal delta = debitAdjust ? advance : advance.negate();
                rows.set(i, new RawRow(row.accountId(), row.name(), row.code(),
                        row.balance().add(delta)));
                return advance;
            }
        }
        return BigDecimal.ZERO;
    }

    /** Сатрлар йиғиндиси. */
    private static BigDecimal sum(List<Row> rows) {
        return rows.stream().map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Гуруҳлар йиғиндиси. */
    private static BigDecimal totalOf(List<Group> groups) {
        return groups.stream().map(Group::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
