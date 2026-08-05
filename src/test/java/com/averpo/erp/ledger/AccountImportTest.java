package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Счётлар режаси CSV импорти тестлари.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountImportTest {

    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;

    @Test
    void importDefaultChart_derivesTypes_andIsIdempotent() {
        AccountService.ImportResult first = accountService.importDefaultChart();
        assertThat(first.created()).isGreaterThan(35);
        assertThat(first.skipped()).isZero();

        // Detail type'дан type ва classification автоматик келиб чиқади
        Account inventory = accountRepository.findByName("Товар-моддий заҳиралар").orElseThrow();
        assertThat(inventory.getType()).isEqualTo(AccountType.OTHER_CURRENT_ASSET);
        assertThat(inventory.getClassification()).isEqualTo(AccountClassification.ASSET);

        Account fx = accountRepository.findByName("Валюта курси фарқи").orElseThrow();
        assertThat(fx.getType()).isEqualTo(AccountType.OTHER_EXPENSE);

        // Иккинчи импорт ҳеч нарсани бузмайди
        AccountService.ImportResult second = accountService.importDefaultChart();
        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(first.created());
    }

    @Test
    void importDefaultChart_existingActiveSystemAccount_warnsAndSkips() {
        // Arbitr-060 жонли ҳодиса: chart'дан ОЛДИН қўлда AP счёти очилган.
        // Импорт chart'нинг AP сатрини яратмай (дубликат тур!), натижага
        // «дубликат тур» огоҳлантириши қўшиши, лекин ЙИҚИЛМАСЛИГИ шарт
        accountService.create("Менинг кредиторларим",
                AccountDetailType.ACCOUNTS_PAYABLE, null, null, null, true, null);

        AccountService.ImportResult result = accountService.importDefaultChart();

        // 51 сатрдан AP'сиз 50 таси яратилди, AP сатри warned+skipped
        assertThat(result.created()).isEqualTo(50);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0))
                .contains("дубликат тур: ACCOUNTS_PAYABLE")
                .contains("Менинг кредиторларим")
                .contains("Кредиторлик (AP)");

        // Chart'нинг ўз AP сатри яратилмаган - резолвер фойдаланувчиникини
        // ягона кўради, Bill оқими ишлайверади (жонли муаммонинг тескариси)
        assertThat(accountRepository.findByName("Кредиторлик (AP)")).isEmpty();
        Account ap = accountService.findSystemAccount(
                AccountDetailType.ACCOUNTS_PAYABLE).orElseThrow();
        assertThat(ap.getName()).isEqualTo("Менинг кредиторларим");

        // Қайта импорт ҳам шу ҳолатда барқарор: яратмайди, яна огоҳлантиради
        AccountService.ImportResult second = accountService.importDefaultChart();
        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(51);
        assertThat(second.warnings()).hasSize(1);
    }

    @Test
    void importDefaultChart_codeCollision_createsWithoutCode_doesNotFail() {
        // Arbitr-126: фойдаланувчи счёти chart кодини банд қилган -
        // uq_account_code туфайли импорт flush'да йиқилмаслиги шарт
        accountService.create("Кодни банд қилган счёт",
                AccountDetailType.SAVINGS, "1010", null, null, true, null);

        AccountService.ImportResult result = accountService.importDefaultChart();

        // Ҳамма 51 сатр яратилди (ном банд эмас), фақат код ташлаб кетилди
        assertThat(result.created()).isEqualTo(51);
        assertThat(result.warnings())
                .anySatisfy(w -> assertThat(w)
                        .contains("код банд")
                        .contains("1010")
                        .contains("Банк ҳисобварағи"));

        Account bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow();
        assertThat(bank.getCode()).isNull();
        // Қолган кодлар жойида - тўқнашмаган сатрлар кодли яратилади
        assertThat(accountRepository.findByName("Касса").orElseThrow().getCode())
                .isEqualTo("1030");
    }

    @Test
    void importDefaultChart_existingParentName_linksChildrenIdempotently() {
        // Ярим ҳолат (Arbitr-126): «Пул маблағлари» номли счёт олдиндан
        // мавжуд - импорт ота сатрини skip қилади, болалар ном бўйича
        // айнан шу мавжуд счётга боғланади, импорт йиқилмайди
        Account existing = accountService.create("Пул маблағлари",
                AccountDetailType.CASH_ON_HAND, null, null, null, true, null);

        AccountService.ImportResult result = accountService.importDefaultChart();

        assertThat(result.created()).isEqualTo(50);
        assertThat(result.skipped()).isEqualTo(1);
        Account kassa = accountRepository.findByName("Касса").orElseThrow();
        assertThat(kassa.getParent().getId()).isEqualTo(existing.getId());
        // Мавжуд счёт ЎЗГАРМАДИ - фойдаланувчи таҳрири импортда бузилмайди
        assertThat(existing.getCode()).isNull();
        assertThat(existing.isPostable()).isTrue();
    }

    @Test
    void systemAccountLookup_ignoresNonPostableGroupParent() {
        // Arbitr-126 МУҲИМ текширув: chart'да «Пул маблағлари» гуруҳ отаси
        // ҳам CASH_ON_HAND - резолвер postable=false отани эмас, айнан
        // postable «Касса»ни танлаши шарт (акс ҳолда default cash BR йиқилади)
        accountService.importDefaultChart();

        Account cash = accountService.findSystemAccount(
                AccountDetailType.CASH_ON_HAND).orElseThrow();
        assertThat(cash.getName()).isEqualTo("Касса");
        assertThat(cash.isPostable()).isTrue();
        assertThat(cash.getParent().getName()).isEqualTo("Пул маблағлари");
    }

    @Test
    void importCsv_wiresParentByName() throws IOException {
        String csv = """
                name;detailType;parentName;postable;currency;code
                Пул маблағлари;CHECKING;;false;;
                Асосий касса;CASH_ON_HAND;Пул маблағлари;true;;
                """;

        accountService.importCsv(new BufferedReader(new StringReader(csv)));

        Account kassa = accountRepository.findByName("Асосий касса").orElseThrow();
        assertThat(kassa.getParent()).isNotNull();
        assertThat(kassa.getParent().getName()).isEqualTo("Пул маблағлари");
        assertThat(kassa.getParent().isPostable()).isFalse();
    }

    @Test
    void importCsv_invalidDetailType_throwsWithLineNumber() {
        String csv = """
                name;detailType;parentName;postable
                Тест счёт;НОТЎҒРИ;;true
                """;

        assertThatThrownBy(() -> accountService.importCsv(
                new BufferedReader(new StringReader(csv))))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("2-қатор");
    }

    @Test
    void importCsv_missingParent_throws() {
        String csv = """
                name;detailType;parentName;postable
                Тест счёт;CHECKING;Йўқ гуруҳ;true
                """;

        assertThatThrownBy(() -> accountService.importCsv(
                new BufferedReader(new StringReader(csv))))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("Йўқ гуруҳ");
    }
}
