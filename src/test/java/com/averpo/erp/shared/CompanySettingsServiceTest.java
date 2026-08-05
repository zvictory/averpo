package com.averpo.erp.shared;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Компания созламалари: home currency қулфи ва timezone валидацияси.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanySettingsServiceTest {

    @Autowired CompanySettingsService settingsService;
    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired CurrencyService currencyService;

    /** Банк счёти - қулф тестидаги проводка учун. */
    private Account bank;

    /** Даромад счёти - қулф тестидаги проводка учун. */
    private Account sales;

    /** Ҳар тест олдидан керакли счётларни яратади. */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, null)));
    }

    @Test
    void defaults_areUzsAndTashkent() {
        assertThat(settingsService.homeCurrency()).isEqualTo("UZS");
        assertThat(settingsService.zoneId().getId()).isEqualTo("Asia/Tashkent");
    }

    @Test
    void homeCurrency_changeable_beforeFirstPosting() {
        CompanySettings updated = settingsService.update(
                "Тест компания", "USD", "Asia/Tashkent", null, null);
        assertThat(updated.homeCurrencyCode()).isEqualTo("USD");
        assertThat(updated.getName()).isEqualTo("Тест компания");
    }

    @Test
    void homeCurrency_unknownCurrency_rejected() {
        assertThatThrownBy(() -> settingsService.update(
                "Тест компания", "XXX", "Asia/Tashkent", null, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("каталогда йўқ");
    }

    @Test
    void timezone_invalid_rejected() {
        assertThatThrownBy(() -> settingsService.update(
                "Тест компания", "UZS", "Mars/Olympus", null, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("timezone");
    }

    @Test
    void timezone_changeable_anytime() {
        CompanySettings updated = settingsService.update(
                "Тест компания", "UZS", "Europe/Berlin", null, null);
        assertThat(updated.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(updated.zoneId().getId()).isEqualTo("Europe/Berlin");
    }

    @Test
    void fiscalYearStartMonth_validChange_persists() {
        CompanySettings updated = settingsService.update(
                "Тест компания", "UZS", "Asia/Tashkent", null, null, 7);
        assertThat(updated.getFiscalYearStartMonth()).isEqualTo(7);

        // Молия йили бошланиши: июлдан олдинги сана ўтган йилга тушади
        assertThat(updated.fiscalYearStart(LocalDate.of(2026, 6, 30)))
                .isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(updated.fiscalYearStart(LocalDate.of(2026, 7, 2)))
                .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void fiscalYearStartMonth_outOfRange_rejected() {
        for (int month : new int[]{0, 13}) {
            assertThatThrownBy(() -> settingsService.update(
                    "Тест компания", "UZS", "Asia/Tashkent", null, null, month))
                    .isInstanceOfSatisfying(
                            com.averpo.erp.shared.exception.BusinessRuleException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BR-SET-004"));
        }
    }

    @Test
    void homeCurrency_locked_afterFirstPosting() {
        BigDecimal amount = new BigDecimal("100000");
        postingService.createAndPost(JournalEntryRequest.manual(
                LocalDate.of(2026, 7, 5), "Қулф тести", List.of(
                        Line.debit(bank.getId(), Money.ofBase(amount, "UZS"), null),
                        Line.credit(sales.getId(), Money.ofBase(amount, "UZS"), null))));

        assertThatThrownBy(() -> settingsService.update(
                "Тест компания", "USD", "Asia/Tashkent", null, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("POSTED");

        // Ном/таймзона ўзгартириш валютага тегмаса - рухсат
        CompanySettings renamed = settingsService.update(
                "Янги ном", "UZS", "Asia/Samarkand", null, null);
        assertThat(renamed.getName()).isEqualTo("Янги ном");
        assertThat(renamed.getTimezone()).isEqualTo("Asia/Samarkand");
    }

    @Test
    void update_marksSetupDone_forOnboarding() {
        // Arbitr-056: янги (бўш) ўрнатишда флаг false - login success handler
        // ADMIN'ни /settings?setup=1 га йўналтиради. Биринчи муваффақиятли
        // сақлашдан кейин true бўлади ва фойдаланувчи қайта йўналтирилмайди.
        assertThat(settingsService.isSetupDone()).isFalse();
        settingsService.update("Тест компания", "UZS", "Asia/Tashkent", null, null);
        assertThat(settingsService.isSetupDone()).isTrue();
    }

    /**
     * Arbitr-112: компания реквизитлари round-trip (сақлаш→қайта ўқиш);
     * email тўлдирилса формат текширилади (BR-SET-007). Реквизитлар
     * GL'га таъсирсиз - қулф йўқ.
     */
    @Test
    void updateCompanyInfo_savesRequisites_rejectsBadEmail() {
        CompanySettings s = settingsService.updateCompanyInfo(
                "Тест Компания Номи", "МЧЖ Тест", "Тошкент ш., Амир Темур 1", "+998711234567",
                "info@test.uz", "test.uz", "301234567", "Ипак Йўли Банки",
                "20208000123456789012", "00014", "Каримов А.А.", "Директор");
        // name (рефайнмент банд 112.1): company info саҳифасида ҳам таҳрирланади
        assertThat(s.getName()).isEqualTo("Тест Компания Номи");
        assertThat(s.getLegalName()).isEqualTo("МЧЖ Тест");
        assertThat(s.getEmail()).isEqualTo("info@test.uz");
        assertThat(s.getBankName()).isEqualTo("Ипак Йўли Банки");

        // Round-trip (қайта ўқиш)
        assertThat(settingsService.get().getBankAccount()).isEqualTo("20208000123456789012");
        assertThat(settingsService.get().getDirectorName()).isEqualTo("Каримов А.А.");

        // BR-SET-007: email формати нотўғри
        assertThatThrownBy(() -> settingsService.updateCompanyInfo(
                null, null, null, null, "notanemail", null, null, null, null, null, null, null))
                .isInstanceOfSatisfying(
                        com.averpo.erp.shared.exception.BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-SET-007"));
    }

    @Test
    void update_inactiveHomeCurrency_autoActivated() {
        // Arbitr-056 банд 6: онбординг формаси ТЎЛИҚ каталогни кўрсатади.
        // Деактив валюта (seed'да EUR нофаол) home сифатида танланса -
        // автоматик активлашади (home валюта нофаол бўла олмайди, PostingService
        // require() қилади - BR-CUR-002). Аввалги хулқ require()'да BR-CUR-002
        // билан йиқиларди.
        assertThat(currencyService.byCode("EUR").orElseThrow().isActive()).isFalse();

        CompanySettings updated = settingsService.update(
                "Тест компания", "EUR", "Asia/Tashkent", null, null);

        assertThat(updated.homeCurrencyCode()).isEqualTo("EUR");
        assertThat(currencyService.byCode("EUR").orElseThrow().isActive()).isTrue();
    }
}
