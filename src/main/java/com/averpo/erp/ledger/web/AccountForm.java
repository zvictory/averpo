package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.domain.Account;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Счёт яратиш/таҳрирлаш формаси. Ҳамма майдон String - хато бўлганда
 * фойдаланувчи киритган қийматлар йўқолмасдан қайта кўрсатилади
 * (BindException ўрнига ўзимиз тушунарли хабар берамиз).
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountForm {

    /** Unique счёт номи. */
    private String name;

    /** Танланган AccountDetailType номи (enum name). */
    private String detailType;

    /** Ихтиёрий счёт рақами. */
    private String code;

    /** Эркин изоҳ. */
    private String description;

    /** Ота счёт id'си (UUID матн кўринишида) ёки бўш. */
    private String parentId;

    /** false - гуруҳ счёти (проводка тақиқ). */
    private boolean postable = true;

    /** Валюта счёти бўлса ISO код. */
    private String currency;

    /** Счёт фаоллиги (фақат таҳрирда кўрсатилади). */
    private boolean active = true;

    /**
     * Очилиш қолдиғи - фақат яратишда, balance-sheet счётлар учун
     * (QBO услуби). Бўш қолса проводка яратилмайди. Сумма счёт
     * валютасида киритилади.
     */
    private String openingBalance;

    /** Очилиш қолдиғи ҳолати санаси (ISO: yyyy-MM-dd). */
    private String openingBalanceDate;

    /** Чет валюта счётида очилиш қолдиғи курси: 1 валюта = ? home. */
    private String openingBalanceRate;

    /** Таҳрир формаси - мавжуд счётдан тўлдирилади. */
    public static AccountForm from(Account account) {
        AccountForm form = new AccountForm();
        form.name = account.getName();
        form.detailType = account.getDetailType().name();
        form.code = account.getCode();
        form.description = account.getDescription();
        form.parentId = account.getParent() == null
                ? null : account.getParent().getId().toString();
        form.postable = account.isPostable();
        form.currency = account.getCurrency() == null
                ? null : account.getCurrency().getCode();
        form.active = account.isActive();
        return form;
    }
}
