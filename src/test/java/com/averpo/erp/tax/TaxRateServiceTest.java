package com.averpo.erp.tax;

import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.tax.domain.TaxRate;
import com.averpo.erp.tax.service.TaxRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaxRate каталоги тестлари: docs/modules/tax.md «Тестлар» - seed
 * мавжуд, код unique, 0..100, нофаол рад, snapshot қиймат.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaxRateServiceTest {

    @Autowired TaxRateService taxRateService;

    /** Seed'даги QQS12 ставкасини топади. */
    private TaxRate qqs12() {
        return taxRateService.all().stream()
                .filter(r -> r.getCode().equals("QQS12")).findFirst().orElseThrow();
    }

    @Test
    void seed_hasStandardRates() {
        assertThat(taxRateService.all()).extracting(TaxRate::getCode)
                .contains("QQS12", "NO_TAX");
        assertThat(qqs12().getRate()).isEqualByComparingTo("12");
        assertThat(taxRateService.activeRates()).isNotEmpty();
    }

    @Test
    void create_duplicateCode_rejected() {
        taxRateService.create("QQS20", "ҚҚС 20%", new BigDecimal("20"));
        // BR-TAX-001: код банд (регистрдан қатъи назар) - 409
        assertThatThrownBy(() -> taxRateService.create("qqs20", "Дубликат", BigDecimal.TEN))
                .satisfies(e -> {
                    assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-001");
                    assertThat(((BusinessRuleException) e).getRule().getHttpStatus()).isEqualTo(409);
                });
    }

    @Test
    void create_validation_guards() {
        // BR-TAX-001: бўш код
        assertThatThrownBy(() -> taxRateService.create(" ", "Ном", BigDecimal.ZERO))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-001"));
        // BR-TAX-005: бўш ном
        assertThatThrownBy(() -> taxRateService.create("X", " ", BigDecimal.ZERO))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-005"));
        // BR-TAX-002: 0..100 чегараси
        assertThatThrownBy(() -> taxRateService.create("NEG", "Манфий", new BigDecimal("-1")))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-002"));
        assertThatThrownBy(() -> taxRateService.create("BIG", "Катта", new BigDecimal("101")))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-002"));
    }

    @Test
    void documentRateValue_activeRules_andSnapshotWins() {
        TaxRate rate = taxRateService.create("QQS15", "ҚҚС 15%", new BigDecimal("15"));

        // snapshot null - каталог қиймати қайтади
        assertThat(taxRateService.documentRateValue(rate.getId(), null))
                .isEqualByComparingTo("15");
        // snapshot берилса - каталог ўзгарса ҳам ЎША қиймат (тарихий ҳужжат)
        assertThat(taxRateService.documentRateValue(rate.getId(), new BigDecimal("15")))
                .isEqualByComparingTo("15");
        taxRateService.update(rate.getId(), "QQS15", "ҚҚС 15%", new BigDecimal("18"), true);
        assertThat(taxRateService.documentRateValue(rate.getId(), new BigDecimal("15")))
                .isEqualByComparingTo("15"); // snapshot, каталогда 18 бўлса ҳам

        // null id - солиқсиз
        assertThat(taxRateService.documentRateValue(null, null)).isNull();

        // BR-TAX-004: каталогда йўқ
        assertThatThrownBy(() -> taxRateService.documentRateValue(UUID.randomUUID(), null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-004"));

        // BR-TAX-003: нофаол ставка танланмайди
        taxRateService.update(rate.getId(), "QQS15", "ҚҚС 15%", new BigDecimal("18"), false);
        assertThatThrownBy(() -> taxRateService.documentRateValue(rate.getId(), null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("BR-TAX-003"));
    }
}
