package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payroll ставкалари (23а) - CompanySettings'да, ADMIN /settings'да
 * таҳрирланади. spec: docs/modules/payroll.md «Ставкалар CompanySettings».
 * Валидация: BR-SET-005 (0..100 фоиз).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollRatesTest {

    @Autowired CompanySettingsService settingsService;

    @Test
    void defaults_areTwelveTenthTwelve() {
        CompanySettings s = settingsService.get();
        assertThat(s.getIncomeTaxRate()).isEqualByComparingTo("12");
        assertThat(s.getPensionRate()).isEqualByComparingTo("0.1");
        assertThat(s.getSocialTaxRate()).isEqualByComparingTo("12");
    }

    @Test
    void updatePayrollRates_validAccepted() {
        settingsService.updatePayrollRates(
                new BigDecimal("15"), new BigDecimal("1"), new BigDecimal("10"));

        CompanySettings s = settingsService.get();
        assertThat(s.getIncomeTaxRate()).isEqualByComparingTo("15");
        assertThat(s.getPensionRate()).isEqualByComparingTo("1");
        assertThat(s.getSocialTaxRate()).isEqualByComparingTo("10");
    }

    @Test
    void rateAbove100_rejectedSet005() {
        assertThatThrownBy(() -> settingsService.updatePayrollRates(
                new BigDecimal("150"), new BigDecimal("1"), new BigDecimal("10")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SET-005"));
    }

    @Test
    void negativeRate_rejectedSet005() {
        assertThatThrownBy(() -> settingsService.updatePayrollRates(
                new BigDecimal("12"), new BigDecimal("-0.5"), new BigDecimal("10")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SET-005"));
    }

    @Test
    void nullRate_rejectedSet005() {
        // Форма tampering (майдонсиз POST) - null ҳам рад
        assertThatThrownBy(() -> settingsService.updatePayrollRates(
                null, new BigDecimal("1"), new BigDecimal("10")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SET-005"));
    }

    @Test
    void boundaryZeroAnd100_accepted() {
        // Чегара: 0 ва 100 - тўғри (BR-SET-005 фақат ташқарисини рад қилади)
        settingsService.updatePayrollRates(
                BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO);
        CompanySettings s = settingsService.get();
        assertThat(s.getPensionRate()).isEqualByComparingTo("100");
        assertThat(s.getIncomeTaxRate()).isEqualByComparingTo("0");
    }
}
