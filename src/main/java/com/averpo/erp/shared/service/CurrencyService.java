package com.averpo.erp.shared.service;

import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.repo.CompanySettingsRepository;
import com.averpo.erp.shared.repo.CurrencyRepository;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Валюта каталогининг public API'си - бошқа модуллар (ledger, contact,
 * item...) каталогга ФАҚАТ шу орқали мурожаат қилади (ТЕМИР ҚОИДА №6:
 * repository'га тегиш тақиқ).
 *
 * @author Zafar
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CurrencyService {

    /** Валюта каталоги репозиторийси. */
    private final CurrencyRepository repository;

    /** Home currency текшируви учун - CompanySettingsService'га боғланиш
     * cycle берарди (у CurrencyService'га боғлиқ); shared модул ичида
     * repo'га мурожаат рухсат (қоида №6 фақат модуллараро). */
    private final CompanySettingsRepository settingsRepository;

    /** Формалардаги select учун фаол валюталар. */
    public List<Currency> active() {
        return repository.findByActiveTrueOrderByCode();
    }

    /** Кодни нормализациялаб (uppercase) каталогдан қидиради. */
    public Optional<Currency> byCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByCode(code.strip().toUpperCase());
    }

    /**
     * Мажбурий фаол валюта - entity'ларга боғлашда ишлатилади.
     *
     * @throws BusinessRuleException BR-CUR-001 - каталогда йўқ,
     *         BR-CUR-002 - нофаол бўлса
     */
    public Currency require(String code) {
        Currency currency = byCode(code)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_CUR_001, "Валюта каталогда йўқ: " + code));
        if (!currency.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_CUR_002, "Валюта фаол эмас: " + code);
        }
        return currency;
    }

    /** Ихтиёрий валюта: бўш код → null, акс ҳолда require. */
    public Currency requireOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return require(code);
    }

    /**
     * Ҳужжат курсининг home/foreign инварианти - пул мантиғи битта жойда,
     * ҳужжат турлари бўйлаб кўчирилмасин деб (Bill, BillPayment, кейин
     * Invoice ҳам шуни ишлатади): home валютада курс фақат 1 (ёки умуман
     * берилмайди), чет валютада мусбат курс шарт. Ҳужжатга хос BR кодни
     * чақирувчи беради - хато фойдаланувчига ўз ҳужжати тилида қайтади.
     *
     * @param currency ҳужжат валютаси (каталогдан олинган)
     * @param rate     фойдаланувчи киритган курс, бўлмаса null
     * @param rule     инвариант бузилганда отиладиган ҳужжатга хос қоида
     *                 (масалан BR-BILL-009, BR-PAY-012)
     * @return home'да доим 1, чет валютада киритилган курс
     * @throws BusinessRuleException берилган rule коди билан - home'да
     *         курс 1 эмас ёки чет валютада курс йўқ/мусбат эмас бўлса
     */
    public BigDecimal requireDocumentRate(Currency currency, BigDecimal rate,
                                          BusinessRule rule) {
        boolean home = currency.getCode().equals(homeCurrencyCode());
        if (home) {
            if (rate != null && rate.compareTo(BigDecimal.ONE) != 0) {
                throw new BusinessRuleException(rule,
                        "Home валютада курс 1 бўлиши шарт, келди: " + rate);
            }
            return BigDecimal.ONE;
        }
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessRuleException(rule,
                    "Чет валюта (" + currency.getCode() + ") ҳужжатида мусбат курс шарт");
        }
        return rate;
    }

    /** UI жадвали учун бутун каталог (нофаоллар билан), код тартибида. */
    public List<Currency> all() {
        return repository.findAll(org.springframework.data.domain.Sort.by("code"));
    }

    /**
     * Валютани фаоллаштириш/нофаол қилиш (QBO Currencies услуби).
     * Нофаол валюта янги ҳужжатларда танланмайди, тарихдагилар бузилмайди.
     *
     * @throws BusinessRuleException BR-CUR-003 - home валюта нофаол қилинса
     */
    @Transactional
    public Currency setActive(String code, boolean active) {
        Currency currency = byCode(code)
                .orElseThrow(() -> new NotFoundException("Валюта топилмади: " + code));
        if (!active && currency.getCode().equals(homeCurrencyCode())) {
            throw new BusinessRuleException(BusinessRule.BR_CUR_003,
                    "Home валюта нофаол қилинмайди: " + currency.getCode());
        }
        currency.update(currency.getName(), currency.getSymbol(), active);
        return currency;
    }

    /**
     * Home currency учун каталогдан валютани олади ва зарур бўлса
     * активлаштиради (Arbitr-056 банд 6). Онбординг/созламалар формаси
     * ТЎЛИҚ каталогни кўрсатади (нофаоллар билан), шунинг учун деактив
     * валюта home сифатида танланиши мумкин - лекин home валюта ҳеч қачон
     * нофаол бўла олмайди (PostingService уни {@link #require} қилади,
     * BR-CUR-002). Шу боис танланганда жимгина активлаштирамиз.
     *
     * @throws BusinessRuleException BR-CUR-001 - каталогда умуман йўқ
     */
    @Transactional
    public Currency activateForHome(String code) {
        Currency currency = byCode(code)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_CUR_001,
                        "Валюта каталогда йўқ: " + code));
        if (!currency.isActive()) {
            currency.update(currency.getName(), currency.getSymbol(), true);
        }
        return currency;
    }

    /** Жорий home валюта коди; созламалар ҳали яратилмаган бўлса default. */
    private String homeCurrencyCode() {
        return settingsRepository.findFirstBy()
                .map(CompanySettings::homeCurrencyCode)
                .orElse(CompanySettings.DEFAULT_HOME_CURRENCY);
    }
}
