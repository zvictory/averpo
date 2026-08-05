package com.averpo.erp.item.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Товар/хизмат - QBO Product/Service элементи.
 *
 * <p>Счёт боғлашлар UUID сифатида сақланади (ledger Account'га JPA
 * relation ЭМАС) - модуллараро қоида (№6) сақланади, мавжудлик
 * ItemService'да ledger'нинг public AccountService'и орқали
 * текширилади. Item ўчирилмайди - фақат inactive (тарихдаги ҳужжат
 * сатрлари бузилмасин).
 */
@Entity
@Table(name = "item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    /** INVENTORY / NON_INVENTORY / SERVICE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ItemType type;

    /** Unique ном - QBO услубида асосий идентификатор. */
    @Column(nullable = false, unique = true)
    private String name;

    /** Ихтиёрий SKU - киритилса unique (partial index). */
    @Column(length = 50)
    private String sku;

    /** Категория ёки null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ItemCategory category;

    /**
     * Ўлчов бирлиги ёки null. UoM киргандан кейин (docs/modules/uom.md)
     * бу BASE бирлик: омбор қолдиғи ва каталог нархлар доим шунда.
     * EAGER - ҳужжат формалари item рўйхатидан factor/ном ўқийди,
     * шаблонда lazy хатоси бўлмасин (Account.currency прецеденти,
     * каталог кичкина - JOIN арзон).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    /**
     * Bill сатрида default бирлик (base билан бир гуруҳдан, BR-ITM-012)
     * ёки null - base ишлатилади. EAGER - unit каби.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_unit_id")
    private Unit purchaseUnit;

    /**
     * Invoice сатрида default бирлик (base билан бир гуруҳдан,
     * BR-ITM-012) ёки null - base ишлатилади. EAGER - unit каби.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sales_unit_id")
    private Unit salesUnit;

    /** Default сотув нархи (home валютада) ёки null. */
    @Column(name = "sales_price", precision = 19, scale = 4)
    private BigDecimal salesPrice;

    /** Invoice сатрига default тавсиф. */
    @Column(name = "sales_description", columnDefinition = "text")
    private String salesDescription;

    /** Даромад счёти id'си (ledger Account) - Invoice post шу ерга кредитлайди. */
    @Column(name = "income_account_id", nullable = false)
    private UUID incomeAccountId;

    /** Default харид нархи (home валютада) ёки null. */
    @Column(name = "purchase_cost", precision = 19, scale = 4)
    private BigDecimal purchaseCost;

    /** Bill сатрига default тавсиф. */
    @Column(name = "purchase_description", columnDefinition = "text")
    private String purchaseDescription;

    /** INVENTORY учун COGS счёти, бошқаларга харажат счёти id'си. */
    @Column(name = "expense_account_id", nullable = false)
    private UUID expenseAccountId;

    /** Inventory asset счёти - фақат INVENTORY типда шарт. */
    @Column(name = "inventory_asset_account_id")
    private UUID inventoryAssetAccountId;

    /** Кам қолдиқ огоҳлантириш чегараси (кейинги босқичларда ишлатилади). */
    @Column(name = "reorder_point", precision = 19, scale = 4)
    private BigDecimal reorderPoint;

    /**
     * Invoice сатрида default ҚҚС ставкаси id'си (dimension, tax модули)
     * ёки null - солиқсиз. FK йўқ - модул мустақиллиги (docs/modules/tax.md).
     */
    @Column(name = "sales_tax_rate_id")
    private UUID salesTaxRateId;

    /** Bill сатрида default ҚҚС ставкаси id'си ёки null - солиқсиз. */
    @Column(name = "purchase_tax_rate_id")
    private UUID purchaseTaxRateId;

    /** QBO make inactive. */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /** Янги item - мажбурий майдонлар билан. */
    public Item(ItemType type, String name, UUID incomeAccountId, UUID expenseAccountId) {
        this.type = type;
        this.name = name;
        this.incomeAccountId = incomeAccountId;
        this.expenseAccountId = expenseAccountId;
    }

    /**
     * Барча таҳрирланадиган майдонларни янгилайди. Тип ўзгармайди -
     * QBO ҳам inventory item'ни service'га айлантиришни чекловлар
     * билан қилади, бизда тақиқ (қолдиқ тарихи бузилмасин).
     */
    public void update(String name, String sku, ItemCategory category, Unit unit,
                       Unit purchaseUnit, Unit salesUnit,
                       BigDecimal salesPrice, String salesDescription,
                       UUID incomeAccountId, BigDecimal purchaseCost,
                       String purchaseDescription, UUID expenseAccountId,
                       UUID inventoryAssetAccountId, BigDecimal reorderPoint) {
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.unit = unit;
        this.purchaseUnit = purchaseUnit;
        this.salesUnit = salesUnit;
        this.salesPrice = salesPrice;
        this.salesDescription = salesDescription;
        this.incomeAccountId = incomeAccountId;
        this.purchaseCost = purchaseCost;
        this.purchaseDescription = purchaseDescription;
        this.expenseAccountId = expenseAccountId;
        this.inventoryAssetAccountId = inventoryAssetAccountId;
        this.reorderPoint = reorderPoint;
    }

    /**
     * Default ҚҚС ставкаларини қўяди (docs/modules/tax.md) - фақат
     * ItemService чақиради. Алоҳида метод: асосий update() имзоси
     * ўсиб кетмасин, tax'сиз чақирувлар (эски тестлар) бузилмасин.
     */
    public void applyTaxDefaults(UUID salesTaxRateId, UUID purchaseTaxRateId) {
        this.salesTaxRateId = salesTaxRateId;
        this.purchaseTaxRateId = purchaseTaxRateId;
    }
}
