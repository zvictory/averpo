package com.averpo.erp.shared.web;

import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Web parse сиёсатининг ягона нуқтаси тестлари: оддий пробел, NBSP,
 * вергул, бўш қиймат ва бузуқ UUID ҳолатлари (QA-003 кутилмаси).
 * Соф unit - Spring контексти шарт эмас.
 */
class FormParsersTest {

    @Test
    void decimal_acceptsSpaceNbspAndComma() {
        // Оддий пробел минг ажратгич + вергул ўнлик
        assertThat(FormParsers.decimal("12 600,50", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("12600.50");

        // NBSP (U+00A0) - money-input.js/Fmt экранидан кўчирилган қиймат
        assertThat(FormParsers.decimal("12 600,50", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("12600.50");

        // Атрофидаги бўшлиқ ва оддий нуқта ҳам ишлайди
        assertThat(FormParsers.decimal("  1000.25 ", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("1000.25");
    }

    /**
     * W2 (DEC-064): мини-калькулятор blur'да ёзадиган форматланган
     * натижа - NBSP гуруҳли, минусли ва касрли кўринишлар - FormParsers
     * орқали айнан қайта ўқилади (калькулятор JS'ини gradle тестламайди,
     * унинг чиқиш ФОРМАТИ server томонда шу ерда қотирилади).
     */
    @Test
    void decimal_acceptsMinusAndCalculatorOutputShapes() {
        // Манфий натижа («=5-8» → «-3»): минус + NBSP гуруҳлаш
        assertThat(FormParsers.decimal("-12 600.50", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("-12600.50");

        // Калькулятор натижаси «=1200*3+500» → экранда «4 100» (NBSP)
        assertThat(FormParsers.decimal("4 100", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("4100");

        // Каср натижа «=5,5*3» → «16.5»; аралаш NBSP + оддий пробел ҳам
        assertThat(FormParsers.decimal("16.5", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("16.5");
        assertThat(FormParsers.decimal("1 200 300,75", BusinessRule.BR_LED_019, "сумма"))
                .isEqualByComparingTo("1200300.75");
    }

    @Test
    void decimal_blankNull_returnsNull() {
        assertThat(FormParsers.decimal(null, BusinessRule.BR_LED_019, "сумма")).isNull();
        assertThat(FormParsers.decimal("   ", BusinessRule.BR_LED_019, "сумма")).isNull();
    }

    @Test
    void decimal_invalid_throwsGivenRule() {
        assertThatThrownBy(() -> FormParsers.decimal("abc", BusinessRule.BR_ITM_010, "Нарх"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-ITM-010"));

        // Хом киритилган қиймат хабарга сизиб чиқмайди (reflected гигиена)
        assertThatThrownBy(() -> FormParsers.decimal("abc", BusinessRule.BR_ITM_010, "Нарх"))
                .hasMessageNotContaining("abc");
    }

    @Test
    void localDate_blankNull_returnsNull_isoParses() {
        assertThat(FormParsers.localDate(null, BusinessRule.BR_SET_006, "Ёпилиш санаси")).isNull();
        assertThat(FormParsers.localDate("  ", BusinessRule.BR_SET_006, "Ёпилиш санаси")).isNull();

        // ISO йил-ой-кун + атрофдаги бўшлиқ
        assertThat(FormParsers.localDate(" 2026-07-09 ", BusinessRule.BR_SET_006, "Ёпилиш санаси"))
                .isEqualTo(java.time.LocalDate.of(2026, 7, 9));
    }

    @Test
    void localDate_invalid_throwsGivenRule_withoutEchoingValue() {
        // Бузуқ формат ҳам, мавжуд бўлмаган сана ҳам rule коди билан рад этилади
        assertThatThrownBy(() -> FormParsers.localDate("09.07.2026", BusinessRule.BR_SET_006,
                "Ёпилиш санаси"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SET-006"))
                .hasMessageNotContaining("09.07.2026");

        assertThatThrownBy(() -> FormParsers.localDate("2026-13-40", BusinessRule.BR_SET_006,
                "Ёпилиш санаси"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SET-006"));
    }

    @Test
    void uuid_blankNull_returnsNull_validParses() {
        assertThat(FormParsers.uuid(null, BusinessRule.NOT_FOUND, "Категория")).isNull();
        assertThat(FormParsers.uuid("  ", BusinessRule.NOT_FOUND, "Категория")).isNull();

        UUID id = UUID.randomUUID();
        assertThat(FormParsers.uuid(" " + id + " ", BusinessRule.NOT_FOUND, "Категория"))
                .isEqualTo(id);
    }

    @Test
    void uuid_invalid_throwsGivenRule_withoutEchoingValue() {
        assertThatThrownBy(() -> FormParsers.uuid("<script>", BusinessRule.BR_LC_003,
                "Receipt танлови"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-003"))
                .hasMessageNotContaining("<script>");
    }

    @Test
    void requireUuid_blankRejected_validParses() {
        assertThatThrownBy(() -> FormParsers.requireUuid(" ", BusinessRule.BR_INV_001,
                "Товар"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-001"));

        assertThatThrownBy(() -> FormParsers.requireUuid("xyz", BusinessRule.BR_INV_001,
                "Товар"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-001"));

        UUID id = UUID.randomUUID();
        assertThat(FormParsers.requireUuid(id.toString(), BusinessRule.BR_INV_001, "Товар"))
                .isEqualTo(id);
    }
}
