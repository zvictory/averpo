package com.averpo.erp.inventory;

import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Омбор CRUD валидациялари: docs/modules/inventory.md → «Тестлар», 1-туртки.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WarehouseServiceTest {

    @Autowired WarehouseService warehouseService;

    @Test
    void seedWarehouse_present() {
        // 017-02 seed: операциялар омборсиз юрмайди
        assertThat(warehouseService.all())
                .extracting(Warehouse::getName)
                .contains("Асосий омбор");
        assertThat(warehouseService.active()).isNotEmpty();
    }

    @Test
    void create_blankOrDuplicateName_rejected() {
        assertThatThrownBy(() -> warehouseService.create("  ", null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-WH-001"));

        assertThatThrownBy(() -> warehouseService.create("Асосий омбор", null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-WH-001"));
    }

    @Test
    void create_duplicateCode_rejected_lowercaseNormalized() {
        // Seed омбор коди MAIN - кичик ҳарфда киритилса ҳам банд
        assertThatThrownBy(() -> warehouseService.create("Филиал омбори", "main"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-WH-002"));

        Warehouse created = warehouseService.create("Филиал омбори", "fil1");
        assertThat(created.getCode()).isEqualTo("FIL1");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void update_keepOwnNameAndCode_allowed_deactivates() {
        Warehouse warehouse = warehouseService.create("Шаҳар омбори", "CITY");

        // Ўз номи/кодини сақлаб фаолликни ўзгартириш - муаммосиз
        Warehouse updated = warehouseService.update(warehouse.getId(),
                "Шаҳар омбори", "CITY", false);
        assertThat(updated.isActive()).isFalse();
        assertThat(warehouseService.active())
                .extracting(Warehouse::getName)
                .doesNotContain("Шаҳар омбори");

        // Бошқа омборнинг номини олиш - тақиқ
        assertThatThrownBy(() -> warehouseService.update(warehouse.getId(),
                "Асосий омбор", null, true))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-WH-001"));
    }

    @Test
    void update_duplicateCode_rejected() {
        // Update'да бошқа омборнинг кодини олиш - тақиқ (BR-WH-002)
        warehouseService.create("Шимол омбори", "NORD");
        Warehouse south = warehouseService.create("Жануб омбори", "SUD");

        assertThatThrownBy(() -> warehouseService.update(south.getId(),
                "Жануб омбори", "NORD", true))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-WH-002"));
    }
}
