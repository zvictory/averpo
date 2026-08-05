package com.averpo.erp.ledger.service;

import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Счёт очилиш қолдиғи (opening balance) - QBO услуби: янги balance-sheet
 * счёт очилаётганда бошланғич қолдиқ киритилса, OPENING_BALANCE_EQUITY
 * счётига қарши автоматик проводка post қилинади (posting-rules.md,
 * «Очилиш қолдиқлари»).
 *
 * <p>sourceModule = OPENING_BALANCE, sourceDocumentId = account id -
 * шу орқали BR-LED-012 idempotency бир счётга иккинчи opening balance
 * киритишни автоматик тўсади; хато бўлса entry reverse қилинади ва
 * қайта киритилади.
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OpeningBalanceService {

    /** GL'даги манба модул белгиси - idempotency калитининг қисми. */
    public static final String SOURCE_MODULE = "OPENING_BALANCE";

    /** Opening balance мумкин бўлган туркумлар (balance sheet). */
    private static final Set<AccountClassification> BALANCE_SHEET = EnumSet.of(
            AccountClassification.ASSET, AccountClassification.LIABILITY,
            AccountClassification.EQUITY);

    /**
     * Бу йўл ёпиқ detail type'лар: AR/AP қолдиғи invoice/bill орқали
     * киради (contact'га боғланиши учун, QBO услуби), OBE'нинг ўзига
     * opening balance маъносиз (қарши счёт ҳам ўзи бўлиб қоларди).
     */
    private static final Set<AccountDetailType> EXCLUDED = EnumSet.of(
            AccountDetailType.ACCOUNTS_RECEIVABLE, AccountDetailType.ACCOUNTS_PAYABLE,
            AccountDetailType.OPENING_BALANCE_EQUITY);

    /** GL'га ёзишнинг ягона йўли (ТЕМИР ҚОИДА №2). */
    private final PostingService postingService;

    /** Счёт ва OBE тизим счётини топиш учун. */
    private final AccountService accountService;

    /** Home currency - OBE сатри доим home валютада. */
    private final CompanySettingsService settingsService;

    /**
     * Счёт яратиш + opening balance киритишни битта транзакцияда
     * бажаради - проводка йиқилса (масалан курс хато) счёт ҳам
     * яратилмай қолади, акс ҳолда фойдаланувчи формани қайта юборганда
     * BR-COA-001 «ном банд» деворига урилар эди.
     */
    public Account createAccountWithOpeningBalance(
            String name, AccountDetailType detailType, String code,
            String description, UUID parentId, boolean postable, String currency,
            BigDecimal amount, LocalDate asOf, BigDecimal exchangeRate) {
        Account account = accountService.create(name, detailType, code,
                description, parentId, postable, currency);
        enter(account.getId(), amount, asOf, exchangeRate);
        return account;
    }

    /**
     * Счётга очилиш қолдиғини киритади ва дарҳол post қилади.
     *
     * <p>Сумма счёт валютасида (QBO услуби). Мусбат сумма счётнинг
     * табиий томонига тушади: актив - дебет, пассив/капитал - кредит;
     * манфий сумма томонларни алмаштиради (масалан overdraft банк).
     *
     * @param accountId    қолдиқ киритилаётган счёт
     * @param amount       сумма счёт валютасида, нолдан фарқли (BR-COA-007)
     * @param asOf         қолдиқ ҳолати санаси - entry шу санага тушади
     * @param exchangeRate чет валюта счётида шарт: 1 валюта = rate home;
     *                     home валютада эътиборга олинмайди
     * @return post қилинган journal entry
     * @throws BusinessRuleException BR-COA-005/006/007 ёки BR-LED-012
     *         (шу счётга opening balance аллақачон киритилган)
     */
    public JournalEntry enter(UUID accountId, BigDecimal amount,
                              LocalDate asOf, BigDecimal exchangeRate) {
        Account account = accountService.get(accountId);
        requireSupported(account);
        if (asOf == null || amount == null || amount.signum() == 0) {
            throw new BusinessRuleException(BusinessRule.BR_COA_007,
                    "Opening balance учун сана ва нолдан фарқли сумма киритилиши шарт");
        }
        Account obe = accountService.findSystemAccount(AccountDetailType.OPENING_BALANCE_EQUITY)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_COA_006,
                        "OPENING_BALANCE_EQUITY тизим счёти топилмади (ягона, фаол, "
                        + "postable бўлиши керак) - аввал default chart'ни юкланг"));

        String home = settingsService.homeCurrency();
        String accountCurrency = account.getCurrency() == null
                ? home : account.getCurrency().getCode();
        Money money;
        if (home.equals(accountCurrency)) {
            money = Money.ofBase(amount.abs(), home);
        } else {
            if (exchangeRate == null || exchangeRate.signum() <= 0) {
                throw new BusinessRuleException(BusinessRule.BR_COA_007,
                        "Чет валюта (" + accountCurrency + ") счётида opening balance "
                        + "учун мусбат курс киритилиши шарт");
            }
            money = Money.of(amount.abs(), accountCurrency, exchangeRate);
        }
        // Қарши сатр доим home валютада - ledger home'да балансланади,
        // OBE'да чет валюта қолдиғи юритилмайди
        Money obeMoney = Money.ofBase(money.getBaseAmount(), home);

        // Мусбат актив -> счёт дебети; пассив/капитал ёки манфий сумма
        // томонни алмаштиради (иккиси бирга бўлса яна дебет)
        boolean debitAccount = (account.getClassification() == AccountClassification.ASSET)
                == (amount.signum() > 0);
        List<JournalEntryRequest.Line> lines = debitAccount
                ? List.of(JournalEntryRequest.Line.debit(account.getId(), money, null),
                          JournalEntryRequest.Line.credit(obe.getId(), obeMoney, null))
                : List.of(JournalEntryRequest.Line.debit(obe.getId(), obeMoney, null),
                          JournalEntryRequest.Line.credit(account.getId(), money, null));

        return postingService.createAndPost(new JournalEntryRequest(
                asOf, "Очилиш қолдиғи: " + account.getName(),
                SOURCE_MODULE, account.getId(), lines));
    }

    /**
     * Шу detail type учун opening balance киритиш мумкинми - UI форма
     * майдонни кўрсатиш/яшириш учун ҳам шу қоидадан фойдаланади
     * (қоида икки жойда такрорланмасин).
     */
    public static boolean supports(AccountDetailType detailType) {
        return BALANCE_SHEET.contains(detailType.getClassification())
                && !EXCLUDED.contains(detailType);
    }

    /** BR-COA-005: фақат balance-sheet, фаол, postable, AR/AP/OBE эмас. */
    private void requireSupported(Account account) {
        if (!supports(account.getDetailType())
                || !account.isPostable() || !account.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_COA_005,
                    "Opening balance фақат balance-sheet (актив/пассив/капитал) фаол "
                    + "postable счёт учун киритилади; AR/AP қолдиғи invoice/bill "
                    + "орқали киради: " + account.getName());
        }
    }
}
