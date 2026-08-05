package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CurrencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Валюта каталоги бошқаруви: фаоллаштириш ва home валюта қулфи.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurrencyServiceTest {

    @Autowired CurrencyService currencyService;

    @Test
    void setActive_togglesForeignCurrency() {
        // Seed'да EUR нофаол - фаоллаштириш ва қайтариш ишлайди
        Currency activated = currencyService.setActive("EUR", true);
        assertThat(activated.isActive()).isTrue();
        assertThat(currencyService.active())
                .extracting(Currency::getCode)
                .contains("EUR");

        Currency deactivated = currencyService.setActive("EUR", false);
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void setActive_homeCurrencyDeactivation_rejected() {
        assertThatThrownBy(() -> currencyService.setActive("UZS", false))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CUR-003"));

        // Home валютани "фаоллаштириш" (аллақачон фаол) - муаммосиз
        assertThat(currencyService.setActive("UZS", true).isActive()).isTrue();
    }

    @Test
    void requireDocumentRate_homeCurrency() {
        Currency home = currencyService.require("UZS");

        // Курс берилмаса ҳам home'да доим 1 қайтади
        assertThat(currencyService.requireDocumentRate(home, null,
                BusinessRule.BR_BILL_009))
                .isEqualByComparingTo(BigDecimal.ONE);

        // Home'да 1 дан фарқли курс - чақирувчи берган ҳужжат коди билан рад
        assertThatThrownBy(() -> currencyService.requireDocumentRate(home,
                new BigDecimal("2"), BusinessRule.BR_BILL_009))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-009"));
    }

    @Test
    void requireDocumentRate_foreignCurrency() {
        Currency usd = currencyService.require("USD");

        // Мусбат курс шундайлигича қайтади
        assertThat(currencyService.requireDocumentRate(usd,
                new BigDecimal("12600"), BusinessRule.BR_PAY_012))
                .isEqualByComparingTo(new BigDecimal("12600"));

        // Курссиз чет валюта ҳужжати - ҳужжатга хос код билан рад
        assertThatThrownBy(() -> currencyService.requireDocumentRate(usd, null,
                BusinessRule.BR_PAY_012))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-012"));
    }
}
