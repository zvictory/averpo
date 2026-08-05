package com.averpo.erp.bank.web;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.shared.domain.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI-005: ўтказмалар рўйхатида манзил сумма ўз валюта коди билан
 * чиқади - счёт → валюта харитаси Spring'сиз соф unit тест билан
 * (DEC-025 тест кутилмаси). PERF-020: ном ва валюта харитаси
 * энди битта циклда (accountViewMaps) - тест шу методга кўчди.
 */
class TransferControllerTest {

    @Test
    void accountViewMaps_namesAndCurrencies_withHomeFallback() {
        Account usdBank = new Account("Валюта счёти", AccountDetailType.CHECKING,
                "1020", null, null, true, new Currency("USD", "АҚШ доллари", "$", true));
        Account uzsBank = new Account("Асосий счёт", AccountDetailType.CHECKING,
                "1010", null, null, true, null);

        TransferController.AccountViewMaps maps =
                TransferController.accountViewMaps(List.of(usdBank, uzsBank), "UZS");

        // Валютали счёт - ўз коди; валютасиз счёт - home коди
        Map<UUID, String> currencies = maps.currencies();
        assertThat(currencies.get(usdBank.getId())).isEqualTo("USD");
        assertThat(currencies.get(uzsBank.getId())).isEqualTo("UZS");
        assertThat(currencies).hasSize(2);
        // Ном харитаси ҳам шу циклда тўлади
        assertThat(maps.names().get(usdBank.getId())).isEqualTo("Валюта счёти");
        assertThat(maps.names().get(uzsBank.getId())).isEqualTo("Асосий счёт");
    }
}
