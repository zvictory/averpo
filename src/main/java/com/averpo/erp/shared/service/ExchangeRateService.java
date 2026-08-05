package com.averpo.erp.shared.service;

import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.ExchangeRate;
import com.averpo.erp.shared.domain.RateSource;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.repo.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Валюта курслари каталогининг public API'си
 * (docs/modules/multi-currency.md). Барча модуллар курсни фақат шу
 * орқали олади: формалардаги prefill, кейинроқ Invoice/Bill default
 * курслари ҳам шу ердан.
 *
 * <p>Курс append-only ТАРИХ (Arbitr-022): бир (валюта, сана)га кўп
 * ёзув - ЦБ ва қўлда/ўтказма ўзгартиришлар устига ёзилмайди. Ёзиш
 * {@link #record} орқали (манба билан); {@link #upsert} - қўлда/MANUAL
 * учун юпқа wrapper. Амалдаги курс = энг охирги ёзув (сана, кейин id).
 *
 * <p><b>Стандарт - IAS 21</b>: операция битим санасидаги курс бўйича
 * функционал валютага ўгирилади, тўлов пайтидаги фарқ фойда-зарарга
 * тушади (realized). Шунинг учун каталог append-only тарих: эски ҳужжат
 * ўз санасидаги курсни доим топади, кейинги ўзгартиришлар уни қайта
 * ёзмайди.
 *
 * <p><b>Маҳаллий мослашув</b>: курслар Ўзбекистон Марказий банкидан
 * кунига икки марта автоматик тортилади ва компания қайси валютада
 * ҳисоб юритишидан қатъи назар тўғри pivot қилинади (ЦБ доим «1 F = N
 * UZS» беради; home UZS бўлмаса қиймат айлантирилади).
 *
 * <p><b>Солиштирув</b>: Xero курсларни XE.com'дан соатига янгилайди,
 * лекин кўп-валютанинг ўзи фақат энг юқори тарифда очилади ва base
 * валютани компания яратилгандан кейин УМУМАН ўзгартириб бўлмайди.
 * Бизда home currency биринчи POSTED проводкагача ўзгартирилади, кейин
 * қулфланади - қоида бор, лекин созлаш ойнаси кенгроқ.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ExchangeRateService {

    /**
     * ЦБ импорти якуни (Arbitr-168): нечта чет валюта ТЕКШИРИЛДИ (ЦБ'да
     * курси топилди ва ёзилди), нечтасининг қиймати аввалги амалдагидан
     * ЎЗГАРДИ, нечтаси ЦБ рўйхатида йўқлиги учун ЎТКАЗИЛДИ. checked
     * changed'дан фарқли: дам олиш/такрор импортда ЦБ эски (жума) курсни
     * қайтаради - валюта текширилади, лекин қиймат ўзгармайди; аудит ва
     * хабар «янгиланди» деб адаштирмаслиги учун иккиси алоҳида саналади.
     */
    public record ImportResult(int checked, int changed, int skipped) { }

    /** Курслар репозиторийси. */
    private final ExchangeRateRepository repository;

    /** Валюта каталоги - код текшируви ва фаол рўйхат учун. */
    private final CurrencyService currencyService;

    /** Home currency текшируви учун (BR-FX-002/003). */
    private final CompanySettingsService settingsService;

    /** ЦБ курс манбаи порти - тестларда сохталанади. */
    private final CbuRateClient cbuRateClient;

    /**
     * Санага тенг ёки ундан ОЛДИНГИ энг охирги курс - курс кундалик,
     * дам олиш/байрам кунида олдингиси амал қилади (QBO услуби).
     * Топилмаса бўш: фойдаланувчи қўлда киритади, тахмин қилинмайди.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> rateFor(String currencyCode, LocalDate date) {
        if (currencyCode == null || currencyCode.isBlank() || date == null) {
            return Optional.empty();
        }
        return repository.findFirstByCurrencyCodeAndRateDateLessThanEqualOrderByRateDateDescIdDesc(
                        currencyCode.strip().toUpperCase(), date)
                .map(ExchangeRate::getRate);
    }

    /** Валютанинг энг охирги курс ёзуви - Currencies экранидаги устун учун. */
    @Transactional(readOnly = true)
    public Optional<ExchangeRate> latest(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByCurrencyCodeOrderByRateDateDescIdDesc(
                currencyCode.strip().toUpperCase());
    }

    /**
     * ҲАР валютанинг амалдаги (энг охирги) ёзуви - код → ёзув харитаси.
     * Currencies экрани учун: ҳар валютага алоҳида {@link #latest} N+1
     * бўлар эди, бу битта window-function сўрови (Beruniy-023).
     */
    @Transactional(readOnly = true)
    public Map<String, ExchangeRate> latestForEachCurrency() {
        Map<String, ExchangeRate> latest = new HashMap<>();
        for (ExchangeRate rate : repository.findLatestForEachCurrency()) {
            latest.put(rate.getCurrency().getCode(), rate);
        }
        return latest;
    }

    /** Валютанинг курс тарихи - янгидан эскига (UI экрани учун). */
    @Transactional(readOnly = true)
    public List<ExchangeRate> history(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return List.of();
        }
        return repository.findByCurrencyCodeOrderByRateDateDescIdDesc(
                currencyCode.strip().toUpperCase());
    }

    /**
     * Курсни ТАРИХГА қайд этиш (append-only): ҳар ўзгариш алоҳида ёзув,
     * устига ёзилмайди - мавжудлари (ЦБ бўлса ҳам) қолади. Айнан бир хил
     * курс шу санага такрор келса дубль ёзилмайди (фақат ўзгаришлар).
     *
     * @throws BusinessRuleException BR-FX-001 (сана/мусбат қиймат шарт),
     *         BR-FX-002 (home валютага тақиқ), BR-CUR-* (каталог хатолари)
     */
    public ExchangeRate record(String currencyCode, LocalDate date,
                               BigDecimal rate, RateSource source) {
        if (date == null || rate == null || rate.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_FX_001,
                    "Курс учун сана ва мусбат қиймат киритилиши шарт");
        }
        Currency currency = currencyService.require(currencyCode);
        if (currency.getCode().equals(settingsService.homeCurrency())) {
            throw new BusinessRuleException(BusinessRule.BR_FX_002,
                    "Home валютага (" + currency.getCode() + ") курс киритилмайди - доим 1");
        }
        // Шу санадаги энг охирги ёзув билан бир хил бўлса дубль йўқ -
        // ЦБ такрор импорти ёки ўзгармаган қўлда қиймат учун
        ExchangeRate current = repository
                .findFirstByCurrencyCodeAndRateDateOrderByIdDesc(currency.getCode(), date)
                .orElse(null);
        if (current != null && current.getRate().compareTo(rate) == 0) {
            return current;
        }
        return repository.save(new ExchangeRate(currency, date, rate, source));
    }

    /** Қўлда/ўтказма орқали курс қайд этиш - {@link #record} MANUAL манба билан. */
    public ExchangeRate upsert(String currencyCode, LocalDate date, BigDecimal rate) {
        return record(currencyCode, date, rate, RateSource.MANUAL);
    }

    /** ЦБ котировкаси доим сўмда - pivot ва home=UZS йўли шу кодга таянади. */
    private static final String UZS = "UZS";

    /** Pivot кросс-курс scale'и - устун NUMERIC(24,12) (changeset 005). */
    private static final int PIVOT_SCALE = 12;

    /**
     * ЦБ'дан кўрсатилган кун курсларини тортиб, барча ФАОЛ чет
     * валюталарга record(CBU) қилади. ЦБ жавобида йўқ валюта ўтказиб
     * юборилади (result.skipped) - импорт бошқаларини тўсмайди.
     *
     * <p>ЦБ ҳамиша «1 F = N UZS» беради. home=UZS бўлса қиймат
     * ЎЗГАРИШСИЗ ёзилади (аввалги йўл - regression йўқ). home≠UZS бўлса
     * кросс-курс UZS орқали pivot қилинади (Arbitr-067, фойдаланувчи
     * талаби 2026-07-10): rate(home per F) = сўм/F ÷ сўм/home, UZS'нинг
     * ўзига 1 ÷ сўм/home - иккиси ҳам scale 12 HALF_UP (устун
     * NUMERIC(24,12)). Кўрсатиш йўналиши бу ерга кирмайди - Arbitr-065.
     *
     * @throws BusinessRuleException BR-FX-001 (сана шарт), BR-FX-003
     *         (home≠UZS ва home валюта ЦБ рўйхатида йўқ - pivot
     *         имконсиз), BR-FX-004 (ЦБ хизмат хатоси)
     */
    public ImportResult importFromCbu(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleException(BusinessRule.BR_FX_001,
                    "Импорт учун сана киритилиши шарт");
        }
        String home = settingsService.homeCurrency();
        Map<String, BigDecimal> cbuRates = new HashMap<>();
        for (CbuRateClient.CbuRate rate : cbuRateClient.rates(date)) {
            // putIfAbsent атайлаб: ЦБ жавобида такрор код кутилмайди,
            // учраб қолса биринчиси олинади - иккинчисини жимгина устига
            // ёзиб юбормаймиз (deterministik натижа, тест ҳам шуни кутади)
            cbuRates.putIfAbsent(rate.currencyCode(), rate.rate());
        }
        // home≠UZS: pivot махражи - home'нинг сўмдаги ЦБ котировкаси.
        // ЦБ рўйхатида бўлмаса (ЦБ котировкаламайдиган home) pivot
        // имконсиз - қўлда киритиш очиқ қолади
        BigDecimal uzsPerHome = null;
        if (!UZS.equals(home)) {
            uzsPerHome = cbuRates.get(home);
            if (uzsPerHome == null || uzsPerHome.signum() <= 0) {
                throw new BusinessRuleException(BusinessRule.BR_FX_003,
                        "Home валюта (" + home + ") ЦБ рўйхатида йўқ - UZS орқали "
                        + "pivot имконсиз, курслар қўлда киритилади");
            }
        }
        int checked = 0;
        int changed = 0;
        int skipped = 0;
        List<Currency> currencies = currencyService.active();
        // Sanjar-011: амалдаги ёзувлар БИР сўровда олдиндан - public record()
        // йўли ҳар валютага уч SELECT (require + home + current) берарди;
        // active() аввал юклангани учун EAGER currency боғлари сессиядан
        // ҳал бўлади. Arbitr-168: effective (rate_date<=date) битта сўровда
        // ҳам «ўзгарди» текшируви, ҳам skip-if-same current'ини беради
        Map<String, ExchangeRate> effectiveByCode = new HashMap<>();
        for (ExchangeRate effective : repository.findLatestEffectivePerCurrencyOn(date)) {
            effectiveByCode.put(effective.getCurrency().getCode(), effective);
        }
        for (Currency currency : currencies) {
            if (currency.getCode().equals(home)) {
                continue; // home'га курс йўқ - доим 1
            }
            BigDecimal rate = importRateFor(currency.getCode(), cbuRates, uzsPerHome);
            if (rate == null || rate.signum() <= 0) {
                skipped++; // ЦБ рўйхатида йўқ (экзотик валюта) - қўлда киритилади
                continue;
            }
            checked++;
            ExchangeRate effective = effectiveByCode.get(currency.getCode());
            // «Ўзгарди» = олдинги амалдаги курс умуман йўқ (илк импорт) ёки
            // қиймат фарқли. Дам олишда ЦБ жума курсини қайтаради - effective
            // билан тенг, changed эмас (санагич ҳалоллиги, Arbitr-168)
            if (effective == null || effective.getRate().compareTo(rate) != 0) {
                changed++;
            }
            // skip-if-same current: эски rate_date=date семантикаси - effective
            // айнан шу санадан бўлсагина append дубль текшируви current'и; бошқа
            // кун бўлса null (A1: per-date ёзув сақланади, ҳар кун текширилди
            // далили Currencies экранида бугунги сана бўлиб кўринади)
            ExchangeRate sameDateCurrent =
                    effective != null && effective.getRateDate().equals(date) ? effective : null;
            recordPreloaded(currency, home, date, rate, RateSource.CBU, sameDateCurrent);
        }
        return new ImportResult(checked, changed, skipped);
    }

    /**
     * Импортнинг ички ёзиш йўли (Sanjar-011): {@link #record} билан айнан
     * бир хил қоида/семантика (BR-FX-001/002 гаровлари, skip-if-same,
     * append-only), лекин олдиндан юкланган {@code Currency}, home ва шу
     * санадаги амалдаги ёзув билан ишлайди - ҳар валютага учта такрор
     * SELECT бермайди. Import оқими home ва мусбат қийматни олдиндан
     * филтрлайди - бу ердаги гаровлар record() контрактининг кўзгуси
     * бўлиб қолиши учун сақланган.
     */
    private ExchangeRate recordPreloaded(Currency currency, String home, LocalDate date,
                                         BigDecimal rate, RateSource source,
                                         ExchangeRate current) {
        if (date == null || rate == null || rate.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_FX_001,
                    "Курс учун сана ва мусбат қиймат киритилиши шарт");
        }
        if (currency.getCode().equals(home)) {
            throw new BusinessRuleException(BusinessRule.BR_FX_002,
                    "Home валютага (" + currency.getCode() + ") курс киритилмайди - доим 1");
        }
        if (current != null && current.getRate().compareTo(rate) == 0) {
            return current;
        }
        return repository.save(new ExchangeRate(currency, date, rate, source));
    }

    /**
     * Битта валютанинг home'даги импорт курси. home=UZS'да (uzsPerHome
     * null) ЦБ қиймати айнан ўзи - бўлиниш йўқ, эски хулқ сақланади;
     * home≠UZS'да UZS орқали pivot (Arbitr-067). Топилмаса null -
     * чақирувчи skipped ҳисоблайди.
     */
    private static BigDecimal importRateFor(String code, Map<String, BigDecimal> cbuRates,
                                            BigDecimal uzsPerHome) {
        if (uzsPerHome == null) {
            return cbuRates.get(code);
        }
        if (UZS.equals(code)) {
            // UZS энди чет валюта: 1 UZS = (1 ÷ сўм/home) home
            return BigDecimal.ONE.divide(uzsPerHome, PIVOT_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal uzsPerUnit = cbuRates.get(code);
        if (uzsPerUnit == null || uzsPerUnit.signum() <= 0) {
            return null;
        }
        return uzsPerUnit.divide(uzsPerHome, PIVOT_SCALE, RoundingMode.HALF_UP);
    }
}
