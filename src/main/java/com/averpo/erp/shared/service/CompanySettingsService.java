package com.averpo.erp.shared.service;

import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.domain.EmailFormat;
import com.averpo.erp.shared.domain.InventoryValuationMethod;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.repo.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Компания созламаларининг ягона кириш нуқтаси.
 *
 * <p>Қатор мавжуд бўлмаса биринчи мурожаатда default қийматлар билан
 * яратилади (seed changeset ўрнига - id UUIDv7 бўлиб қолиши учун).
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CompanySettingsService {

    /** Созламалар қатори репозиторийси. */
    private final CompanySettingsRepository repository;

    /** Home currency каталог орқали боғланади. */
    private final CurrencyService currencyService;

    /** Home currency қулфи портлари (ledger имплементацияси). */
    private final ObjectProvider<HomeCurrencyLock> locks;

    /** Valuation қулфи портлари (inventory модули 5-босқичда беради). */
    private final ObjectProvider<InventoryValuationLock> valuationLocks;

    /**
     * Аудит event'и учун (Arbitr-062): shared audit'ни import қила
     * олмайди (audit BaseEntity орқали shared'га боғлиқ - цикл чиқарди),
     * шунга update ўз event'ини эълон қилади - ledger нақши.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Созламалар қатори - йўқ бўлса default билан яратилади.
     *
     * <p>Оддий {@code save} (flush ЭМАС): бу метод read-only оқимлардан
     * ҳам чақирилади (homeCurrency/zoneId - ҳужжат формалари) - читал
     * транзакцияда {@code saveAndFlush} Hibernate флешини мажбурлаб
     * хато берарди. Лого оқими jdbc EXISTS учун flush талаб қилса
     * {@link #persistedId()} ишлатади (ёзувчи транзакцияда).
     */
    public CompanySettings get() {
        return repository.findFirstBy().orElseGet(() ->
                repository.save(new CompanySettings(
                        "Компания",
                        currencyService.require(CompanySettings.DEFAULT_HOME_CURRENCY),
                        CompanySettings.DEFAULT_TIMEZONE)));
    }

    /**
     * Company_settings қаторини DB'га flush қилиб id'сини қайтаради
     * (Arbitr-112 лого оқими): AttachmentService.targetExists ХОМ jdbc
     * EXISTS билан текширгани учун қатор DB'да кўринмаса BR-ATT-003
     * отиларди. ФАҚАТ ёзувчи транзакциядан (CompanyInfoService.uploadLogo)
     * чақирилади - {@code get()}'нинг read-only тузоғи бу ерда йўқ.
     */
    public java.util.UUID persistedId() {
        CompanySettings settings = get();
        repository.saveAndFlush(settings);
        return settings.getId();
    }

    /** Ledger baseAmount'лари айнан шу валютада (ISO код). */
    public String homeCurrency() {
        return get().homeCurrencyCode();
    }

    /** Вақтларни экранга чиқариш минтақаси (темир қоида №12). */
    public ZoneId zoneId() {
        return get().zoneId();
    }

    /** 5-босқичда inventory модули COGS ҳисоблашда шу методга қарайди. */
    public InventoryValuationMethod valuationMethod() {
        return get().getInventoryValuation();
    }

    /** Давр ёпилиш санаси - PostingService BR-LED-020 текширувида ишлатади (null - қулф йўқ). */
    public java.time.LocalDate closingDate() {
        return get().getClosingDate();
    }

    /**
     * Онбординг тугаганми (Arbitr-056) - login success handler ADMIN'ни
     * false бўлса /settings?setup=1 га йўналтиради. Қатор йўқ бўлса get()
     * default билан яратади (setupDone=false) - янги ўрнатиш setup'дан ўтади.
     */
    public boolean isSetupDone() {
        return get().isSetupDone();
    }

    /** Class tracking режими - ҳужжат формалари UI'ни шунга қараб қуради. */
    public com.averpo.erp.shared.domain.ClassTrackingMode trackClasses() {
        return get().getTrackClasses();
    }

    /**
     * Class tracking режимини алмаштиради (class-tracking.md): қулф йўқ,
     * эски ҳужжатлар ўзгармайди. Атайлаб умумий update()'дан ТАШҚАРИДА -
     * режим танлагичи /settings/classes экранида туради (каталог билан
     * ёнма-ён), компания созламалари формасига қўшилмаган.
     */
    public void changeTrackClasses(com.averpo.erp.shared.domain.ClassTrackingMode mode) {
        get().changeTrackClasses(mode == null
                ? com.averpo.erp.shared.domain.ClassTrackingMode.OFF : mode);
    }

    /** Созламалар экрани учун: home currency қулфланганми (POSTED бор). */
    public boolean homeCurrencyLocked() {
        return locks.stream().anyMatch(HomeCurrencyLock::locked);
    }

    /** Созламалар экрани учун: valuation методи қулфланганми. */
    public boolean valuationLocked() {
        return valuationLocks.stream().anyMatch(InventoryValuationLock::locked);
    }

    /**
     * Ном, home currency (ISO код), timezone, inventory valuation методи
     * ва давр ёпилиш санасини янгилаш.
     *
     * <p>closingDate ҳар қандай сана (ёки null - қулфни олиш) бўлиши
     * мумкин - орқага/олдинга суриш ADMIN ихтиёрида (QBO ҳам чекламайди).
     * Ёпиқ даврга тушиб қолган draft'лар шунчаки post бўлмайди (BR-LED-020).
     *
     * @throws BusinessRuleException BR-SET-001 (валюта қулфланган),
     *         BR-SET-002 (нотўғри timezone), BR-SET-003 (valuation
     *         қулфланган) ёки BR-CUR-* (валюта каталог хатолари)
     */
    public CompanySettings update(String name, String homeCurrencyCode,
                                  String timezone, InventoryValuationMethod valuation,
                                  java.time.LocalDate closingDate) {
        return update(name, homeCurrencyCode, timezone, valuation, closingDate, null);
    }

    /**
     * Тўлиқ янгилаш - молия йили бошланиш ойи билан (9-босқич).
     * fiscalYearStartMonth null бўлса жорий қиймат сақланади (эски
     * чақирувчилар ва формасиз йўллар учун).
     *
     * @throws BusinessRuleException BR-SET-004 (ой 1..12 оралиғида эмас)
     *         ва {@link #update(String, String, String, InventoryValuationMethod,
     *         java.time.LocalDate)} даги қоидалар
     */
    public CompanySettings update(String name, String homeCurrencyCode,
                                  String timezone, InventoryValuationMethod valuation,
                                  java.time.LocalDate closingDate,
                                  Integer fiscalYearStartMonth) {
        CompanySettings settings = get();
        // Аудит диффи учун эски қийматлар snapshot'и (Arbitr-062,
        // SETTINGS_CHANGED): мутациялардан кейин солиштирилиб фақат
        // ростдан ўзгарган майдонлар event'га киради
        String oldName = settings.getName();
        String oldCurrency = settings.homeCurrencyCode();
        String oldTimezone = settings.getTimezone();
        InventoryValuationMethod oldValuation = settings.getInventoryValuation();
        java.time.LocalDate oldClosing = settings.getClosingDate();
        int oldFiscalMonth = settings.getFiscalYearStartMonth();
        settings.rename(name);
        settings.changeClosingDate(closingDate);

        if (!settings.homeCurrencyCode().equals(
                homeCurrencyCode == null ? null : homeCurrencyCode.strip().toUpperCase())) {
            if (homeCurrencyLocked()) {
                throw new BusinessRuleException(BusinessRule.BR_SET_001,
                        "Home currency ўзгартирилмайди: тизимда POSTED проводкалар бор");
            }
            // Arbitr-056 банд 6: онбординг формаси ТЎЛИҚ каталогни кўрсатади -
            // танланган валюта деактив бўлса activateForHome уни автоматик
            // активлаштиради (home валюта нофаол бўла олмайди - BR-CUR-002).
            // Қулф текшируви аввал: қулфланганда каталогга умуман тегмаймиз.
            settings.changeHomeCurrency(currencyService.activateForHome(homeCurrencyCode));
        }

        if (!settings.getTimezone().equals(timezone)) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw new BusinessRuleException(BusinessRule.BR_SET_002, "Нотўғри timezone: " + timezone);
            }
            settings.changeTimezone(timezone);
        }

        if (valuation != null && settings.getInventoryValuation() != valuation) {
            if (valuationLocked()) {
                throw new BusinessRuleException(BusinessRule.BR_SET_003,
                        "Inventory valuation методи ўзгартирилмайди: омбор ҳаракатлари бор");
            }
            settings.changeInventoryValuation(valuation);
        }

        if (fiscalYearStartMonth != null
                && fiscalYearStartMonth != settings.getFiscalYearStartMonth()) {
            if (fiscalYearStartMonth < 1 || fiscalYearStartMonth > 12) {
                throw new BusinessRuleException(BusinessRule.BR_SET_004,
                        "Молия йили бошланиш ойи 1..12 оралиғида бўлиши керак: " + fiscalYearStartMonth);
            }
            settings.changeFiscalYearStartMonth(fiscalYearStartMonth);
        }
        // Онбординг (Arbitr-056): исталган йўл билан муваффақиятли сақлангач
        // флаг ёқилади - иккала update() overload'и ва контроллер шу ягона
        // нуқтага делегация қилади, шунинг учун бир жойда белгилаш кифоя.
        settings.markSetupDone();
        // Аудит (Arbitr-062): фақат ЎЗГАРГАН майдонлар; ўзгаришсиз сақлаш
        // event бермайди. setupDone диффга кирмайди - ички онбординг флаги,
        // фойдаланувчи «ўзгартирган» созлама эмас.
        List<String> changes = new ArrayList<>();
        diff(changes, "name", oldName, settings.getName());
        diff(changes, "homeCurrency", oldCurrency, settings.homeCurrencyCode());
        diff(changes, "timezone", oldTimezone, settings.getTimezone());
        diff(changes, "inventoryValuation", oldValuation, settings.getInventoryValuation());
        diff(changes, "closingDate", oldClosing, settings.getClosingDate());
        diff(changes, "fiscalYearStartMonth", oldFiscalMonth, settings.getFiscalYearStartMonth());
        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(
                    new CompanySettingsChangedEvent(String.join("; ", changes)));
        }
        return settings;
    }

    /**
     * ФАҚАТ давр ёпилиш санасини ўзгартиради (user-roles.md PERIOD_CLOSE
     * оқими): CHIEF_ACCOUNTANT'да SETTINGS рухсати йўқ - тўлиқ update()
     * формасига кира олмайди, шунга {@link #changeTrackClasses} нақшидаги
     * тор метод. Бошқа қулфланадиган майдонларга тегмайди, onboarding
     * флагини ҳам ўзгартирмайди; аудит диффи update() билан бир хил
     * event орқали ёзилади - даврни ким очиб-ёпгани изсиз қолмайди.
     */
    public CompanySettings changeClosingDate(java.time.LocalDate closingDate) {
        CompanySettings settings = get();
        java.time.LocalDate oldClosing = settings.getClosingDate();
        settings.changeClosingDate(closingDate);
        List<String> changes = new ArrayList<>();
        diff(changes, "closingDate", oldClosing, settings.getClosingDate());
        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(
                    new CompanySettingsChangedEvent(String.join("; ", changes)));
        }
        return settings;
    }

    /** Қиймат ўзгарган бўлса диффга «майдон: эски → янги» қатори қўшади. */
    private static void diff(List<String> changes, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(field + ": " + (oldValue == null ? "-" : oldValue)
                    + " → " + (newValue == null ? "-" : newValue));
        }
    }

    /**
     * Payroll ставкаларини янгилаш (/settings формаси, ADMIN). Учаласи
     * фоиз, 0..100 оралиғида (BR-SET-005) - form tampering'га қарши сервер
     * гарови. {@link #changeTrackClasses} каби умумий {@code update()}'дан
     * ТАШҚАРИДА, лекин шу форманинг қисми (контроллер ёнма-ён чақиради).
     * Қулф йўқ: эски POSTED run'лар snapshot сумма туфайли ўзгармайди.
     *
     * @throws BusinessRuleException BR-SET-005 - ставка null ёки 0..100'дан ташқари
     */
    public void updatePayrollRates(java.math.BigDecimal incomeTaxRate,
                                   java.math.BigDecimal pensionRate,
                                   java.math.BigDecimal socialTaxRate) {
        requireRate(incomeTaxRate, "Даромад солиғи ставкаси");
        requireRate(pensionRate, "Пенсия бадали ставкаси");
        requireRate(socialTaxRate, "Ижтимоий солиқ ставкаси");
        get().changePayrollRates(incomeTaxRate, pensionRate, socialTaxRate);
    }

    /** BR-SET-005: ставка киритилган ва 0..100 фоиз оралиғида бўлиши шарт. */
    private void requireRate(java.math.BigDecimal rate, String label) {
        if (rate == null || rate.signum() < 0
                || rate.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_SET_005,
                    label + " 0..100 фоиз оралиғида бўлиши керак");
        }
    }

    /**
     * Компания реквизитларини янгилайди (Arbitr-112, /settings/company
     * формаси): юридик ном/манзил/алоқа/банк/директор. Ҳаммаси ихтиёрий -
     * бўш қиймат тозалайди (null). email тўлдирилса формати текширилади
     * (BR-SET-007). Реквизитлар GL/posting'га таъсирсиз (қулф йўқ).
     *
     * @throws BusinessRuleException BR-SET-007 - email формати нотўғри
     */
    public CompanySettings updateCompanyInfo(String name, String legalName, String address,
                                             String phone, String email, String website,
                                             String taxId, String bankName, String bankAccount,
                                             String bankMfo, String directorName,
                                             String directorPosition) {
        String cleanEmail = blankToNull(email);
        if (cleanEmail != null && !EmailFormat.isValid(cleanEmail)) {
            throw new BusinessRuleException(BusinessRule.BR_SET_007,
                    "Компания email формати нотўғри");
        }
        CompanySettings settings = get();
        // Company name (рефайнмент банд 112.1): company info саҳифасида ҳам
        // таҳрирланади (Созлама билан бир хил майдон). Бўш - ўзгартирмайди
        // (name NOT NULL - форма required, tampered бўшни ўтказмаймиз).
        if (name != null && !name.isBlank()) {
            settings.rename(name.strip());
        }
        settings.updateCompanyInfo(blankToNull(legalName), blankToNull(address),
                blankToNull(phone), cleanEmail, blankToNull(website), blankToNull(taxId),
                blankToNull(bankName), blankToNull(bankAccount), blankToNull(bankMfo),
                blankToNull(directorName), blankToNull(directorPosition));
        return settings;
    }

    /**
     * Лого attachment id'сини ўрнатади (null - олиб ташлаш) ва дарҳол
     * flush қилади - чақирувчи (CompanyInfoService) шундан кейин эски
     * attachment'ни ўчиради, FK янги логога ёзилгач (ON DELETE SET NULL
     * янги боғланишни бузмайди, 101 аватар нақши). Attachment'нинг ЎЗини
     * бу метод бошқармайди (shared модул attachment'га боғланмайди -
     * фақат UUID FK).
     */
    public void setLogoAttachmentId(java.util.UUID logoAttachmentId) {
        CompanySettings settings = get();
        settings.setLogoAttachmentId(logoAttachmentId);
        repository.saveAndFlush(settings);
    }

    /** Жорий лого attachment id'си (GET /settings/company/logo учун; null - йўқ). */
    public java.util.UUID logoAttachmentId() {
        return get().getLogoAttachmentId();
    }

    /**
     * Бренд логоси (топбар, Arbitr-112 рефайнмент) FK'сини ўрнатади ва
     * flush қилади - {@link #setLogoAttachmentId} нақши (ҳужжат
     * логосидан ФАРҚли иккинчи attachment). Файлни чақирувчи
     * (CompanyInfoService) бошқаради; бу метод фақат UUID FK.
     */
    public void setBrandLogoAttachmentId(java.util.UUID brandLogoAttachmentId) {
        CompanySettings settings = get();
        settings.setBrandLogoAttachmentId(brandLogoAttachmentId);
        repository.saveAndFlush(settings);
    }

    /** Жорий бренд логоси attachment id'си (GET /company/brand-logo учун; null - йўқ). */
    public java.util.UUID brandLogoAttachmentId() {
        return get().getBrandLogoAttachmentId();
    }

    /** Бўш/фақат-бўшлиқ матнни null'га айлантиради (ихтиёрий реквизитлар учун). */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
