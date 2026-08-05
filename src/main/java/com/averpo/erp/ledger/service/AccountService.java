package com.averpo.erp.ledger.service;

import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.DefaultChartInstaller;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Счётлар режаси CRUD ва CSV импорти.
 *
 * <p>CSV формати (биринчи қатор - сарлавҳа, ажратгич «;»):
 * {@code name;detailType;parentName;postable;currency;code}.
 * Счёт номи unique идентификатор (QBO услуби), код ихтиёрий.
 * Импорт идемпотент: мавжуд номлар ўтказиб юборилади - фойдаланувчи
 * таҳрирлаган счётлар қайта импортда бузилмайди.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountService implements DefaultChartInstaller {

    /**
     * Иерархияни текис рўйхатда кўрсатиш учун битта тугун.
     *
     * @param account     счётнинг ўзи
     * @param depth       илдиздан чуқурлик (indent учун)
     * @param hasChildren болалари борми - tree view'да chevron кўрсатиш учун
     * @param ancestors   ота занжири id'лари - бирортаси йиғилган бўлса
     *                    қатор яширилади
     */
    public record AccountNode(Account account, int depth, boolean hasChildren,
                              List<UUID> ancestors) { }

    /**
     * Импорт натижаси: нечта яратилди, нечтаси мавжудлиги учун ўтказилди.
     *
     * @param created  янги яратилган счётлар сони
     * @param skipped  яратилмаганлар: ном банд (idempotent skip) ёки тизим
     *                 турида фаол дубликат (warnings'да изоҳланади)
     * @param warnings импорт йиқилмайдиган, лекин фойдаланувчи билиши
     *                 керак ҳолатлар: BR-COA-010 «дубликат тур: ...»
     *                 (Arbitr-060) ва «код банд: ...» - код тўқнашганда
     *                 счёт кодсиз яратилади (Arbitr-126)
     */
    public record ImportResult(int created, int skipped, List<String> warnings) { }

    /** Bundled default chart жойлашуви (QBO default chart услубида). */
    private static final String DEFAULT_CHART_RESOURCE = "coa/default-chart.csv";

    /**
     * Тизим счёти резолвери ({@link #requireSystemAccount}) «ягона» талаб
     * қиладиган detail type'лар: шуларда иккинчи ФАОЛ счёт BR-COA-010 билан
     * рад этилади (Arbitr-060 - жонли серверда дубликат AP резолверни
     * BR-LED-021 га йиқитиб Bill оқимини синдирган эди). Манба - резолвер
     * чақириқлари grep'и (posting service'лар + OpeningBalanceService).
     *
     * <p>PAYROLL_EXPENSES атайлаб КИРМАЙДИ: бу турда атайлаб бир нечта
     * счёт бор (иккита postable харажат + «Иш ҳақи харажатлари» гуруҳ
     * отаси, Arbitr-126), payroll НОМ бўйича топади
     * ({@code PayrollRunService}). CHECKING/CASH_ON_HAND каби фойдаланувчи
     * хоҳлаганча очадиган турлар ҳам кирмайди - улар резолвер орқали
     * эмас, формада танланади.
     */
    private static final Set<AccountDetailType> UNIQUE_SYSTEM_DETAIL_TYPES = Set.of(
            AccountDetailType.ACCOUNTS_RECEIVABLE,
            AccountDetailType.ACCOUNTS_PAYABLE,
            AccountDetailType.INVENTORY,
            AccountDetailType.INVENTORY_CLEARING,
            AccountDetailType.SALES_TAX_PAYABLE,
            AccountDetailType.OPENING_BALANCE_EQUITY,
            AccountDetailType.EXCHANGE_GAIN_OR_LOSS,
            AccountDetailType.SUPPLIES_MATERIALS_COGS,
            AccountDetailType.OTHER_COSTS_OF_SERVICE_COS,
            AccountDetailType.PAYROLL_CLEARING);

    /** Счётлар режаси тартиби: тур бўйича, кейин ном бўйича (QBO услуби). */
    private static final Comparator<Account> CHART_ORDER =
            Comparator.comparing((Account a) -> a.getType().ordinal())
                    .thenComparing(Account::getName);

    /** Счётлар репозиторийси. */
    private final AccountRepository repository;

    /** Валюта каталоги - currency коди entity'га боғлашдан олдин текширилади. */
    private final CurrencyService currencyService;

    /**
     * Аудит event'лари учун (Arbitr-062): ledger audit'ни import қила
     * олмагани учун (қоида №6) create/update/importDefaultChart ўз
     * ҳодисаларини эълон қилади - JournalEntryPostedEvent нақши.
     */
    private final ApplicationEventPublisher eventPublisher;

    /** Барча счётлар QBO тартибида (тур, кейин ном). */
    @Transactional(readOnly = true)
    public List<Account> all() {
        List<Account> accounts = repository.findAll();
        accounts.sort(CHART_ORDER);
        return accounts;
    }

    /**
     * Счётлар режаси БУТУНЛАЙ бўшми (count==0). Янги ўрнатиш bootstrap'и
     * ({@code DefaultChartInitializer}, Arbitr-059) default chart'ни ФАҚАТ
     * шу ҳолда автоматик юклайди - бўш-эмас база (қисман ўчирилган chart
     * ҳам) тегилмайди. {@code all()}'дан фарқли: бутун жадвални юкламай
     * енгил {@code count(*)} қилади (bootstrap ҳар кўтарилишда чақиради).
     */
    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return repository.count() == 0;
    }

    /** Проводка формасидаги select учун: фаол ва postable счётлар. */
    @Transactional(readOnly = true)
    public List<Account> postableAccounts() {
        List<Account> accounts = repository.findByActiveTrueAndPostableTrue();
        accounts.sort(CHART_ORDER);
        return accounts;
    }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Счёт топилмади: " + id));
    }

    /**
     * Сўралган id'лар бўйича счётлар битта IN сўровда (Arbitr-045
     * findAllById нақши) - ҳужжат service'лари сатр-циклда счётни
     * қайта-қайта {@link #get} билан юкламасин (Sanjar-003/008).
     * Топилмаганлар рўйхатда бўлмайди; мавжудликни чақирувчи ўз сатр
     * хатоси билан текширади.
     */
    @Transactional(readOnly = true)
    public List<Account> findAllById(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findAllById(ids);
    }

    /**
     * Тизим счётини detail type орқали топади (posting-rules.md услуби).
     * Бошқа модуллар default счётларни шу орқали олади - фақат биттагина
     * фаол postable счёт бўлса қайтади, акс ҳолда empty (фойдаланувчи
     * ўзи танлайди).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Account> findSystemAccount(
            com.averpo.erp.ledger.domain.AccountDetailType detailType) {
        List<Account> found = activePostableByType(detailType);
        return found.size() == 1 ? java.util.Optional.of(found.get(0))
                : java.util.Optional.empty();
    }

    /**
     * Тизим счётини МАЖБУРИЙ топади - автоматик проводка ёзадиган
     * service'лар учун. findSystemAccount'дан фарқи: топилмаса шу ерда
     * BR-LED-021 отилади, шунда «ягона фаол postable» талқини ва хато
     * матни posting service'лар бўйлаб такрорланмайди (ҳар янги
     * автоматик проводка шу методни чақиради, ўзи orElseThrow ёзмайди).
     *
     * <p>Хабар ҳолатга қараб иккига ажралган (Arbitr-060): «топилмади»
     * (chart юкланмаган) ва «бир нечта топилди: номлар» (legacy дубликат -
     * фойдаланувчи қайсиларини деактив қилишни кўради). Умумий хабар
     * жонли серверда сабабни тушунтира олмаган эди; дубликатнинг ўзи энди
     * BR-COA-010 билан тўсилади, бу шох фақат эски маълумот учун қолади.
     *
     * @throws BusinessRuleException BR-LED-021 - detail type бўйича
     *         ягона фаол postable счёт топилмаса (0 ёки 2+ бўлса)
     */
    @Transactional(readOnly = true)
    public Account requireSystemAccount(
            com.averpo.erp.ledger.domain.AccountDetailType detailType) {
        List<Account> found = activePostableByType(detailType);
        if (found.size() == 1) {
            return found.get(0);
        }
        if (found.isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_LED_021,
                    detailType + " тизим счёти топилмади (фаол, postable бўлиши "
                    + "керак) - аввал default chart'ни юкланг");
        }
        throw new BusinessRuleException(BusinessRule.BR_LED_021,
                detailType + " тизим счёти бир нечта топилди: " + names(found)
                + " - биттасини деактив қилинг");
    }

    /** Detail type'даги фаол postable счётлар - тизим счёти резолвери хом рўйхати. */
    private List<Account> activePostableByType(AccountDetailType detailType) {
        return repository.findByDetailType(detailType).stream()
                .filter(a -> a.isActive() && a.isPostable())
                .toList();
    }

    /** Счёт номларини хато хабарига «ном», «ном» кўринишида тизади. */
    private static String names(List<Account> accounts) {
        return accounts.stream()
                .map(a -> "«" + a.getName() + "»")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * {@link #requireSystemAccount} нинг id варианти - posting
     * service'ларга айнан id керак; аввал ҳар бири ўз хусусий
     * «.getId() wrapper»ини тутарди (Beruniy-backlog2), энди BR-LED-021
     * стек изи ҳам тўғридан-тўғри AccountService'ни кўрсатади.
     *
     * @throws BusinessRuleException BR-LED-021 - тизим счёти топилмаса
     */
    @Transactional(readOnly = true)
    public UUID requireSystemAccountId(
            com.averpo.erp.ledger.domain.AccountDetailType detailType) {
        return requireSystemAccount(detailType).getId();
    }

    /**
     * Дарахтни DFS тартибида текислайди - JTE жадвалда depth бўйича
     * indent бериш учун. Ота'си рўйхатда бўлмаган (аномал) счётлар
     * илдиз сифатида кўрсатилади, яширилмайди.
     */
    @Transactional(readOnly = true)
    public List<AccountNode> tree() {
        List<Account> all = all();
        Map<UUID, List<Account>> children = new HashMap<>();
        List<Account> roots = new ArrayList<>();
        for (Account account : all) {
            Account parent = account.getParent();
            if (parent == null) {
                roots.add(account);
            } else {
                children.computeIfAbsent(parent.getId(), k -> new ArrayList<>()).add(account);
            }
        }
        List<AccountNode> result = new ArrayList<>(all.size());
        for (Account root : roots) {
            flatten(root, 0, List.of(), children, result);
        }
        return result;
    }

    /** DFS ёрдамчиси - болаларни ота остига чуқурлик ва ота занжири билан қўяди. */
    private void flatten(Account account, int depth, List<UUID> ancestors,
                         Map<UUID, List<Account>> children, List<AccountNode> out) {
        List<Account> kids = children.getOrDefault(account.getId(), List.of());
        out.add(new AccountNode(account, depth, !kids.isEmpty(), ancestors));
        if (kids.isEmpty()) {
            return;
        }
        List<UUID> childAncestors = new ArrayList<>(ancestors);
        childAncestors.add(account.getId());
        for (Account child : kids) {
            flatten(child, depth + 1, childAncestors, children, out);
        }
    }

    /**
     * Янги счёт яратади. Ном unique, код (агар киритилса) unique.
     *
     * @throws BusinessRuleException ном бўш (BR-COA-009), detail type
     *         танланмаган (BR-COA-008), ном/код банд (BR-COA-001/002),
     *         тизим турида фаол счёт аллақачон бўлса (BR-COA-010) ёки валюта
     *         валютага боғланмаган турга берилса (BR-COA-011)
     */
    public Account create(String name, AccountDetailType detailType, String code,
                          String description, UUID parentId, boolean postable,
                          String currency) {
        String normalizedName = requireName(name);
        requireDetailType(detailType);
        if (repository.existsByName(normalizedName)) {
            throw new BusinessRuleException(BusinessRule.BR_COA_001, "Бу ном банд: " + normalizedName);
        }
        String normalizedCode = normalize(code);
        if (normalizedCode != null && repository.existsByCode(normalizedCode)) {
            throw new BusinessRuleException(BusinessRule.BR_COA_002, "Бу код банд: " + normalizedCode);
        }
        // Янги счёт доим active=true туғилади - тизим турида дубликат тўсилади
        requireNoActiveSystemDuplicate(detailType, null);
        Account parent = parentId == null ? null : get(parentId);
        com.averpo.erp.shared.domain.Currency resolvedCurrency = currencyService.requireOrNull(currency);
        requireCurrencyAllowedForType(detailType, resolvedCurrency);
        Account account = repository.save(new Account(normalizedName, detailType,
                normalizedCode, Strings.blankToNull(description), parent, postable,
                resolvedCurrency));
        // Аудит (Arbitr-062): форма ва Excel import йўли иккиси шу ердан ўтади
        eventPublisher.publishEvent(new AccountChangedEvent(account,
                AccountChangedEvent.Action.CREATED, null));
        return account;
    }

    /**
     * BR-COA-011: валюта фақат валютага боғланган турга берилади (банк,
     * дебиторлик, кредиторлик, кредит карта - {@code AccountType.isCurrencyDenominated()}
     * ягона манба). Бошқа турга валюта берилса рад этилади; валюта бўш
     * (null) бўлса ҳар турга рухсат. Форма x-show шу ягона манбадан ўқийди.
     */
    private void requireCurrencyAllowedForType(AccountDetailType detailType,
                                               com.averpo.erp.shared.domain.Currency currency) {
        if (currency != null && !detailType.getType().isCurrencyDenominated()) {
            throw new BusinessRuleException(BusinessRule.BR_COA_011,
                    "Валюта фақат банк, дебиторлик, кредиторлик ёки кредит карта туридаги счётга берилади");
        }
    }

    /**
     * Счётни таҳрирлайди.
     *
     * @throws BusinessRuleException ном бўш/детал тури танланмаган
     *         (BR-COA-009/008), ном/код бошқа счётда банд (BR-COA-001/002),
     *         танланган ота иерархияда цикл ҳосил қилса (BR-COA-003),
     *         фаол ҳолатда тизим турида бошқа фаол счёт бўлса - жумладан
     *         деактивни қайта активлаштиришда (BR-COA-010) - ёки валюта
     *         валютага боғланмаган турга берилса (BR-COA-011)
     */
    public Account update(UUID id, String name, AccountDetailType detailType,
                          String code, String description, UUID parentId,
                          boolean postable, String currency, boolean active) {
        Account account = get(id);
        String normalizedName = requireName(name);
        requireDetailType(detailType);
        repository.findByName(normalizedName)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_COA_001, "Бу ном банд: " + normalizedName);
                });
        String normalizedCode = normalize(code);
        if (normalizedCode != null) {
            repository.findByCode(normalizedCode)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new BusinessRuleException(BusinessRule.BR_COA_002, "Бу код банд: " + normalizedCode);
                    });
        }
        Account parent = parentId == null ? null : get(parentId);
        requireNoCycle(account, parent);
        if (active) {
            // Активлаштириш ҳам, фаол счёт турини алмаштириш ҳам шу ердан
            // ўтади - тизим турида иккинчи фаол счёт пайдо бўлмайди
            requireNoActiveSystemDuplicate(detailType, id);
        }
        // Аудит диффи учун эски қийматлар snapshot'и - update'дан ОЛДИН
        // (Arbitr-062: details'да фақат ЎЗГАРГАН майдонлар «эски → янги»)
        List<String> changes = new ArrayList<>();
        boolean wasActive = account.isActive();
        diff(changes, "name", account.getName(), normalizedName);
        diff(changes, "detailType", account.getDetailType(), detailType);
        diff(changes, "code", account.getCode(), normalizedCode);
        diff(changes, "description", account.getDescription(), Strings.blankToNull(description));
        diff(changes, "parent", account.getParent() == null ? null : account.getParent().getName(),
                parent == null ? null : parent.getName());
        diff(changes, "postable", account.isPostable(), postable);
        diff(changes, "currency", account.getCurrency() == null ? null : account.getCurrency().getCode(),
                normalize(currency));
        diff(changes, "active", wasActive, active);
        com.averpo.erp.shared.domain.Currency resolvedCurrency = currencyService.requireOrNull(currency);
        requireCurrencyAllowedForType(detailType, resolvedCurrency);
        account.update(normalizedName, detailType, normalizedCode, Strings.blankToNull(description),
                parent, postable, resolvedCurrency);
        account.setActive(active);
        if (wasActive && !active) {
            // Нофаол қилиш - алоҳида тур (spec жадвали); бошқа ўзгаришлар
            // ҳам бўлса диффда бирга кўринади
            eventPublisher.publishEvent(new AccountChangedEvent(account,
                    AccountChangedEvent.Action.DEACTIVATED, joinChanges(changes)));
        } else if (!changes.isEmpty()) {
            // Ўзгаришсиз сақлаш event бермайди - журнал шовқинланмайди
            eventPublisher.publishEvent(new AccountChangedEvent(account,
                    AccountChangedEvent.Action.UPDATED, joinChanges(changes)));
        }
        return account;
    }

    /** Қиймат ўзгарган бўлса диффга «майдон: эски → янги» қатори қўшади. */
    private static void diff(List<String> changes, String field, Object oldValue, Object newValue) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            changes.add(field + ": " + (oldValue == null ? "-" : oldValue)
                    + " → " + (newValue == null ? "-" : newValue));
        }
    }

    /** Дифф рўйхатини details матнига бирлаштиради (бўш - null). */
    private static String joinChanges(List<String> changes) {
        return changes.isEmpty() ? null : String.join("; ", changes);
    }

    /**
     * BR-COA-009: ном мажбурий. Валидация service ичида - tampered
     * request (name параметрисиз POST) controller'да NPE=500 бермай,
     * аниқ бизнес хато билан қайтади (ContactService паттерни).
     */
    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_COA_009, "Счёт номи киритилиши шарт");
        }
        return name.strip();
    }

    /** BR-COA-008: detail type мажбурий - форма tampering'ига қарши ҳимоя. */
    private void requireDetailType(AccountDetailType detailType) {
        if (detailType == null) {
            throw new BusinessRuleException(BusinessRule.BR_COA_008, "Detail type танланиши шарт");
        }
    }

    /**
     * BR-COA-010 (Arbitr-060): резолвер «ягона» кутадиган тизим detail
     * type'ида иккита фаол счёт бўлса {@link #requireSystemAccount}
     * BR-LED-021 билан бутун ҳужжат оқимини (Bill, Invoice, тўлов...)
     * тўхтатади - шунинг учун дубликат ишлатиш пайтида эмас, айнан
     * ЯРАТИШ/АКТИВЛАШТИРИШ пайтида рад этилади. postable'га қаралмайди:
     * фаол гуруҳ счёти ҳам турни банд қилади (карта қоидаси - «актив
     * мавжуд бўлса рад»); деактив счёт тўсиқ эмас.
     *
     * @param selfId таҳрирланаётган счётнинг ўз id'си - ўзи ўзига дубликат
     *               саналмайди (яратишда {@code null})
     * @throws BusinessRuleException BR-COA-010 - турда бошқа фаол счёт бор
     */
    private void requireNoActiveSystemDuplicate(AccountDetailType detailType, UUID selfId) {
        if (!UNIQUE_SYSTEM_DETAIL_TYPES.contains(detailType)) {
            return;
        }
        repository.findByDetailType(detailType).stream()
                .filter(Account::isActive)
                .filter(other -> !other.getId().equals(selfId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new BusinessRuleException(BusinessRule.BR_COA_010,
                            "Бу тизим тури (" + detailType + ") учун фаол счёт аллақачон "
                            + "бор: «" + existing.getName() + "» - иккинчисини очиш ўрнига "
                            + "ўшани ишлатинг ёки аввал уни деактив қилинг");
                });
    }

    /**
     * Иерархия цикл ҳимояси: янги ота занжирида счётнинг ўзи учрамаслиги
     * керак - акс ҳолда tree() илдиз тополмай счётлар рўйхатдан
     * «йўқолади». Ўзига ота бўлиш ҳам шу текширувга киради.
     */
    private void requireNoCycle(Account account, Account newParent) {
        Account cursor = newParent;
        int guard = 0;
        while (cursor != null) {
            if (cursor.getId().equals(account.getId())) {
                throw new BusinessRuleException(BusinessRule.BR_COA_003, "Иерархияда цикл: «" + account.getName()
                        + "» ўз шажарасидаги счётга ота бўла олмайди");
            }
            if (++guard > 100) {
                // 100 дан чуқур занжир - маълумот аллақачон бузилган
                throw new BusinessRuleException(BusinessRule.BR_COA_003, "Счёт иерархияси жуда чуқур ёки бузилган");
            }
            cursor = cursor.getParent();
        }
    }

    /**
     * {@link DefaultChartInstaller} порти (заводга қайтариш): shared'даги
     * {@code FactoryResetService} счётлар жадвалини TRUNCATE қилгач шу
     * метод орқали default chart'ни қайта ўрнатади - тескари боғлиқликсиз
     * (ledger -> shared). Idempotent {@link #importDefaultChart} устига
     * ўралади, натижа кўрсаткичи reset учун керак эмас.
     */
    @Override
    public void installDefaultChart() {
        importDefaultChart();
    }

    /**
     * Bundled QBO услуб default chart'ни импорт қилади. Аудит
     * (Arbitr-062): ҳар чақириқ CHART_IMPORTED event'и билан из қолдиради
     * - қўлда тугма, авто-init ва factory reset учаласи шу ердан ўтади
     * (идемпотент қайта чақириқда created=0 бўлади, из барибир фойдали:
     * КИМ импортга уринганини кўрсатади).
     */
    public ImportResult importDefaultChart() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(DEFAULT_CHART_RESOURCE).getInputStream(),
                StandardCharsets.UTF_8))) {
            ImportResult result = importCsv(reader);
            eventPublisher.publishEvent(new ChartImportedEvent(
                    result.created(), result.skipped()));
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Default chart ресурси ўқилмади", e);
        }
    }

    /**
     * Икки босқичли импорт: аввал ҳамма счёт ота'сиз яратилади, кейин
     * иерархия боғланади - файлдаги тартибга боғлиқ қолмаслик учун.
     *
     * <p>Тизим тури ҳимояси (Arbitr-060): тизим турида БОШҚА номли фаол
     * счёт турган бўлса ўша сатр яратилмайди, лекин импорт ҳам йиқилмайди -
     * натижага «дубликат тур: ...» огоҳлантириши қўшилади (BR-COA-010 нинг
     * импорт кўриниши: жонли серверда қўлда очилган AP chart'даги AP билан
     * дубль бериб Bill оқимини синдирган эди). Ном бўйича idempotent skip
     * бундан олдин, индамай ишлайверади.
     *
     * <p>Код ҳимояси (Arbitr-126): CSV'даги код мавжуд счётда (ёки шу
     * импортда аввалроқ яратилганда) банд бўлса счёт КОДСИЗ яратилади ва
     * warnings'га ёзилади - акс ҳолда {@code uq_account_code} unique
     * index flush пайтида бутун импортни йиқитарди. Ном бўйича
     * idempotency кодга қараганда устувор.
     *
     * <p>Чеклов: parser оддий {@code split(";")} - quoted field'ларни
     * билмайди, счёт номида «;» бўлса парсинг бузилади. Мураккаб CSV
     * керак бўлганда OpenCSV каби кутубхонага ўтилади.
     *
     * @throws BusinessRuleException BR-COA-004 - бузуқ CSV қатори
     *         (хабарда қатор рақами)
     * @throws IOException reader'дан ўқиш узилса (format хатолари бунга
     *         кирмайди - улар BR-COA-004)
     */
    public ImportResult importCsv(BufferedReader reader) throws IOException {
        /** Битта CSV қатори - парслангандан кейинги ҳолат. */
        record CsvRow(int lineNo, String name, AccountDetailType detailType,
                      String parentName, boolean postable, String currency,
                      String code) { }

        List<CsvRow> rows = new ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.toLowerCase().startsWith("name")) {
                continue; // сарлавҳа ёки бўш қатор
            }
            String[] parts = trimmed.split(";", -1);
            if (parts.length < 4) {
                throw new BusinessRuleException(BusinessRule.BR_COA_004, lineNo
                        + "-қатор: камида 4 устун керак (name;detailType;parentName;postable)");
            }
            AccountDetailType detailType;
            try {
                detailType = AccountDetailType.valueOf(parts[1].strip().toUpperCase());
            } catch (IllegalArgumentException e) {
                // valueOf'нинг хом IAE'си бизнес қоидага ўралади
                throw new BusinessRuleException(BusinessRule.BR_COA_004,
                        lineNo + "-қатор: нотўғри detailType «" + parts[1].strip() + "»");
            }
            rows.add(new CsvRow(lineNo, parts[0].strip(), detailType,
                    parts[2].strip(),
                    parseStrictBoolean(lineNo, parts[3].strip()),
                    parts.length > 4 ? normalize(parts[4]) : null,
                    parts.length > 5 ? normalize(parts[5]) : null));
        }

        Map<String, Account> byName = new HashMap<>();
        // Тизим турини банд қилган фаол счётлар - BR-COA-010 огоҳлантириши
        // учун; findAll бир айланишда учала тўпламни тўлдиради
        Map<AccountDetailType, Account> activeSystemByType = new HashMap<>();
        // Банд кодлар - uq_account_code unique index'ига flush'да урилиб
        // бутун импортни йиқитмаслик учун олдиндан тўпланади (Arbitr-126)
        Set<String> occupiedCodes = new HashSet<>();
        for (Account existing : repository.findAll()) {
            byName.put(existing.getName(), existing);
            if (existing.getCode() != null) {
                occupiedCodes.add(existing.getCode());
            }
            if (existing.isActive()
                    && UNIQUE_SYSTEM_DETAIL_TYPES.contains(existing.getDetailType())) {
                activeSystemByType.putIfAbsent(existing.getDetailType(), existing);
            }
        }

        // 1-босқич: янги счётларни ота'сиз яратиш
        Set<String> createdNames = new HashSet<>();
        int skipped = 0;
        List<String> warnings = new ArrayList<>();
        for (CsvRow row : rows) {
            if (byName.containsKey(row.name())) {
                skipped++;
                continue;
            }
            Account occupant = activeSystemByType.get(row.detailType());
            if (occupant != null) {
                // Тизим турида фаол дубликат - сатр яратилмайди, импорт
                // йиқилмайди: фойдаланувчи сабабни натижа хабарида кўради
                skipped++;
                warnings.add("дубликат тур: " + row.detailType() + " - фаол «"
                        + occupant.getName() + "» тургани учун «" + row.name()
                        + "» яратилмади");
                continue;
            }
            com.averpo.erp.shared.domain.Currency currency;
            try {
                currency = currencyService.requireOrNull(row.currency());
                requireCurrencyAllowedForType(row.detailType(), currency);
            } catch (BusinessRuleException e) {
                throw new BusinessRuleException(BusinessRule.BR_COA_004, row.lineNo() + "-қатор: " + e.getMessage());
            }
            String code = row.code();
            if (code != null && !occupiedCodes.add(code)) {
                // Код бошқа счётда банд (uq_account_code) - счёт КОДСИЗ
                // яратилади, импорт йиқилмайди: ном idempotency'си кодга
                // қараганда муҳимроқ, кодни фойдаланувчи кейин ўзи беради
                warnings.add("код банд: «" + code + "» бошқа счётда тургани "
                        + "учун «" + row.name() + "» кодсиз яратилди");
                code = null;
            }
            Account account = repository.save(new Account(row.name(), row.detailType(),
                    code, null, null, row.postable(), currency));
            byName.put(row.name(), account);
            createdNames.add(row.name());
            if (UNIQUE_SYSTEM_DETAIL_TYPES.contains(row.detailType())) {
                // Файл ичидаги иккинчи шу турдаги сатр ҳам огоҳлантирилсин
                activeSystemByType.putIfAbsent(row.detailType(), account);
            }
        }

        // 2-босқич: янги яратилганларга ота боғлаш
        for (CsvRow row : rows) {
            if (!createdNames.contains(row.name()) || row.parentName().isEmpty()) {
                continue;
            }
            Account parent = byName.get(row.parentName());
            if (parent == null) {
                throw new BusinessRuleException(BusinessRule.BR_COA_004, row.lineNo() + "-қатор: parent топилмади: " + row.parentName());
            }
            Account account = byName.get(row.name());
            account.update(account.getName(), account.getDetailType(), account.getCode(),
                    account.getDescription(), parent, account.isPostable(),
                    account.getCurrency());
        }
        return new ImportResult(createdNames.size(), skipped, warnings);
    }

    /**
     * Қатъий boolean парс: true/false/1/0 - бошқа қиймат хато.
     * Boolean.parseBoolean ярамайди: у нотаниш қийматни жимгина
     * false қилиб юборади - импортда яширин хато манбаи.
     */
    private boolean parseStrictBoolean(int lineNo, String value) {
        return switch (value.toLowerCase()) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new BusinessRuleException(BusinessRule.BR_COA_004, lineNo + "-қатор: postable учун true/false/1/0 кутилган эди, "
                    + "келди: «" + value + "»");
        };
    }

    /** Валюта/код каби қисқа майдонларни тозалайди: бўш → null, upper-case. */
    private String normalize(String value) {
        if (value == null) return null;
        String v = value.strip().toUpperCase();
        return v.isEmpty() ? null : v;
    }

}
