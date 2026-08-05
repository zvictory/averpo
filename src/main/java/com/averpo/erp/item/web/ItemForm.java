package com.averpo.erp.item.web;

import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.web.FormParsers;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Item яратиш/таҳрирлаш формаси. Ҳамма майдон String - хато бўлганда
 * фойдаланувчи киритган қийматлар йўқолмасдан қайта кўрсатилади.
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemForm {

    /** ItemType enum номи - фақат яратишда танланади. */
    private String type = "SERVICE";

    /** Unique ном. */
    private String name;

    /** Ихтиёрий SKU. */
    private String sku;

    /** Категория id'си ёки бўш. */
    private String categoryId;

    /** Бирлик id'си ёки бўш (UoM: base бирлик). */
    private String unitId;

    /** Default харид бирлиги id'си ёки бўш (BR-ITM-012: base гуруҳидан). */
    private String purchaseUnitId;

    /** Default сотув бирлиги id'си ёки бўш (BR-ITM-012: base гуруҳидан). */
    private String salesUnitId;

    /** Default сотув нархи (home валютада). */
    private String salesPrice;

    /** Invoice сатрига default тавсиф. */
    private String salesDescription;

    /** Даромад счёти id'си. */
    private String incomeAccountId;

    /** Default харид нархи. */
    private String purchaseCost;

    /** Bill сатрига default тавсиф. */
    private String purchaseDescription;

    /** Харажат/COGS счёти id'си. */
    private String expenseAccountId;

    /** Inventory asset счёти - фақат INVENTORY типда. */
    private String inventoryAssetAccountId;

    /** Invoice сатрида default ҚҚС ставкаси id'си ёки бўш (docs/modules/tax.md). */
    private String salesTaxRateId;

    /** Bill сатрида default ҚҚС ставкаси id'си ёки бўш. */
    private String purchaseTaxRateId;

    /** Фаоллик - фақат таҳрирда кўрсатилади. */
    private boolean active = true;

    /** Таҳрир формаси - мавжуд item'дан тўлдирилади. */
    public static ItemForm from(Item item) {
        ItemForm form = new ItemForm();
        form.type = item.getType().name();
        form.name = item.getName();
        form.sku = item.getSku();
        form.categoryId = item.getCategory() == null
                ? null : item.getCategory().getId().toString();
        form.unitId = item.getUnit() == null ? null : item.getUnit().getId().toString();
        form.purchaseUnitId = item.getPurchaseUnit() == null
                ? null : item.getPurchaseUnit().getId().toString();
        form.salesUnitId = item.getSalesUnit() == null
                ? null : item.getSalesUnit().getId().toString();
        form.salesPrice = item.getSalesPrice() == null
                ? null : item.getSalesPrice().stripTrailingZeros().toPlainString();
        form.salesDescription = item.getSalesDescription();
        form.incomeAccountId = item.getIncomeAccountId().toString();
        form.purchaseCost = item.getPurchaseCost() == null
                ? null : item.getPurchaseCost().stripTrailingZeros().toPlainString();
        form.purchaseDescription = item.getPurchaseDescription();
        form.expenseAccountId = item.getExpenseAccountId().toString();
        form.inventoryAssetAccountId = item.getInventoryAssetAccountId() == null
                ? null : item.getInventoryAssetAccountId().toString();
        form.salesTaxRateId = item.getSalesTaxRateId() == null
                ? null : item.getSalesTaxRateId().toString();
        form.purchaseTaxRateId = item.getPurchaseTaxRateId() == null
                ? null : item.getPurchaseTaxRateId().toString();
        form.active = item.isActive();
        return form;
    }

    /**
     * Service қабул қиладиган кўринишга айлантиради. Parse қоидаси
     * FormParsers'да: бузуқ танлов қийматлари (tampered select) хом
     * IllegalArgumentException эмас, майдонга мос BR код билан қайтади.
     */
    public ItemService.ItemData toData() {
        return new ItemService.ItemData(name, sku,
                FormParsers.uuid(categoryId, BusinessRule.NOT_FOUND, "Категория"),
                FormParsers.uuid(unitId, BusinessRule.NOT_FOUND, "Бирлик"),
                FormParsers.decimal(salesPrice, BusinessRule.BR_ITM_010, "Нарх"),
                salesDescription,
                FormParsers.uuid(incomeAccountId, BusinessRule.BR_ITM_008, "Даромад счёти"),
                FormParsers.decimal(purchaseCost, BusinessRule.BR_ITM_010, "Нарх"),
                purchaseDescription,
                FormParsers.uuid(expenseAccountId, BusinessRule.BR_ITM_008, "Харажат счёти"),
                FormParsers.uuid(inventoryAssetAccountId, BusinessRule.BR_ITM_008,
                        "Inventory asset счёти"),
                null,
                FormParsers.uuid(purchaseUnitId, BusinessRule.NOT_FOUND, "Харид бирлиги"),
                FormParsers.uuid(salesUnitId, BusinessRule.NOT_FOUND, "Сотув бирлиги"),
                FormParsers.uuid(salesTaxRateId, BusinessRule.BR_TAX_004, "Сотув ҚҚС"),
                FormParsers.uuid(purchaseTaxRateId, BusinessRule.BR_TAX_004, "Харид ҚҚС"));
    }
}
