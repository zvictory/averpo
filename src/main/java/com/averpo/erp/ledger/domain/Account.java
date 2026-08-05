package com.averpo.erp.ledger.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Счётлар режасидаги битта счёт - QBO Chart of Accounts услубида.
 *
 * <p>Фойдаланувчи {@link AccountDetailType} танлайди; {@code type} ва
 * {@code classification} ундан келиб чиқади, лекин ҳисобот SQL'лари
 * JOIN'сиз ишлаши учун учаласи ҳам устун сифатида сақланади (ягона
 * ҳақиқат манбаи - Java enum mapping, {@link #applyDetailType}).
 *
 * <p>Ном unique (QBO услуби). Код ихтиёрий - тизим счётлари код орқали
 * эмас, detail type орқали топилади. Иерархия {@code parent} орқали:
 * гуруҳ счётлари ({@code postable=false}) фақат тузилма учун,
 * проводка фақат postable счётларга ёзилади (3-инвариант).
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    /** Unique ном - QBO услубида счётнинг асосий идентификатори. */
    @Column(nullable = false, unique = true)
    private String name;

    /** Фундаментал синф - detail type'дан келиб чиқади, ҳисоботлар учун сақланади. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountClassification classification;

    /** QBO Account Type - detail type'дан келиб чиқади. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountType type;

    /** Фойдаланувчи танлайдиган ягона тур майдони. */
    @Enumerated(EnumType.STRING)
    @Column(name = "detail_type", nullable = false, length = 40)
    private AccountDetailType detailType;

    /** Ихтиёрий счёт рақами (QBO Account numbers) - киритилса unique. */
    @Column(length = 10)
    private String code;

    /** Счёт нимага ишлатилиши ҳақида эркин изоҳ (QBO Description). */
    @Column(columnDefinition = "text")
    private String description;

    /** Ота счёт - sub-account бўлса (QBO «is sub-account»). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parent;

    /** false - гуруҳ счёти, унга проводка тақиқ (3-инвариант). */
    @Column(nullable = false)
    private boolean postable = true;

    /**
     * Валюта счёти бўлса Currency каталогига боғлаш (масалан USD банк),
     * акс ҳолда null. EAGER - рўйхат шаблонида lazy хатоси бўлмасин
     * (каталог кичкина, JOIN арзон).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id")
    private com.averpo.erp.shared.domain.Currency currency;

    /** Нофаол счёт янги ҳужжатларда танланмайди, тарихда қолади. */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /** Янги счёт - тур майдонлари detail type'дан автоматик тўлдирилади. */
    public Account(String name, AccountDetailType detailType, String code,
                   String description, Account parent, boolean postable,
                   com.averpo.erp.shared.domain.Currency currency) {
        this.name = name;
        applyDetailType(detailType);
        this.code = code;
        this.description = description;
        this.parent = parent;
        this.postable = postable;
        this.currency = currency;
    }

    /**
     * Таҳрирлаш - тур ўзгарса type/classification ҳам қайта ҳисобланади.
     * Тарихий проводкалар detail type ўзгаришидан таъсирланмайди:
     * улар счёт id'сига боғланган.
     */
    public void update(String name, AccountDetailType detailType, String code,
                       String description, Account parent, boolean postable,
                       com.averpo.erp.shared.domain.Currency currency) {
        this.name = name;
        applyDetailType(detailType);
        this.code = code;
        this.description = description;
        this.parent = parent;
        this.postable = postable;
        this.currency = currency;
    }

    /** Ягона жой: detail type → type → classification занжирини қўллайди. */
    private void applyDetailType(AccountDetailType detailType) {
        this.detailType = detailType;
        this.type = detailType.getType();
        this.classification = detailType.getClassification();
    }
}
