package com.averpo.erp.item;

import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemCategoryService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Item CRUD валидациялари - spec: docs/modules/item.md.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemServiceTest {

    @Autowired ItemService itemService;
    @Autowired ItemCategoryService categoryService;
    @Autowired AccountService accountService;

    /** Default chart'дан келадиган тизим счёт id'лари. */
    private ItemService.DefaultAccounts defaults;

    /** Ҳар тест олдидан default chart импорт қилинади (rollback тозалайди). */
    @BeforeEach
    void importChart() {
        accountService.importDefaultChart();
        defaults = itemService.defaultsFor(ItemType.INVENTORY);
    }

    /** Минимал INVENTORY item маълумоти. */
    private ItemData inventoryData(String name) {
        return new ItemData(name, null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null);
    }

    @Test
    void defaults_resolvedByDetailType() {
        // Default chart'да ҳар system detail type'дан биттадан счёт бор
        assertThat(defaults.income()).isNotNull();
        assertThat(defaults.expense()).isNotNull();
        assertThat(defaults.inventoryAsset()).isNotNull();

        // SERVICE тип учун даромад default'и бошқа (хизмат даромади)
        ItemService.DefaultAccounts serviceDefaults = itemService.defaultsFor(ItemType.SERVICE);
        assertThat(serviceDefaults.income()).isNotEqualTo(defaults.income());
    }

    @Test
    void create_inventoryItem_ok() {
        Item item = itemService.create(ItemType.INVENTORY, inventoryData("Клавиатура K120"));
        assertThat(item.getType()).isEqualTo(ItemType.INVENTORY);
        assertThat(item.getInventoryAssetAccountId()).isEqualTo(defaults.inventoryAsset());
    }

    @Test
    void create_inventoryWithoutAssetAccount_rejected() {
        ItemData data = new ItemData("Асset'сиз товар", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null);

        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, data))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("inventory asset");
    }

    @Test
    void create_inventoryAssetWrongDetailType_rejected() {
        // Даромад счётини inventory asset сифатида бериш - detail type мос эмас
        ItemData data = new ItemData("Нотўғри asset", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.income(), null);

        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, data))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("INVENTORY");
    }

    @Test
    void create_wrongClassificationAccounts_rejected() {
        // Даромад счёти ўрнига харажат счёти - REVENUE талаби ишлайди
        ItemData wrongIncome = new ItemData("Нотўғри даромад", null, null, null,
                null, null, defaults.expense(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null);
        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, wrongIncome))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("REVENUE");

        // Харажат счёти ўрнига даромад счёти - EXPENSE талаби ишлайди
        ItemData wrongExpense = new ItemData("Нотўғри харажат", null, null, null,
                null, null, defaults.income(), null, null, defaults.income(),
                defaults.inventoryAsset(), null);
        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, wrongExpense))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("EXPENSE");
    }

    @Test
    void create_duplicateNameOrSku_rejected() {
        itemService.create(ItemType.INVENTORY, inventoryData("Дубликат товар"));
        assertThatThrownBy(() ->
                itemService.create(ItemType.INVENTORY, inventoryData("Дубликат товар")))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("банд");

        ItemData withSku = new ItemData("SKU товар 1", "SKU-1", null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null);
        itemService.create(ItemType.INVENTORY, withSku);
        ItemData sameSku = new ItemData("SKU товар 2", "SKU-1", null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null);
        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, sameSku))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("SKU");
    }

    @Test
    void category_cycleRejected() {
        var a = categoryService.create("Электроника", null);
        var b = categoryService.create("Ноутбуклар", a.getId());

        assertThatThrownBy(() ->
                categoryService.update(a.getId(), "Электроника", b.getId(), true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("цикл");
    }

    @Test
    void unknownCategoryOrUnit_rejected() {
        ItemData data = new ItemData("Бегона категория", null, UUID.randomUUID(), null,
                null, null, defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null);

        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, data))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("Категория");
    }
}
