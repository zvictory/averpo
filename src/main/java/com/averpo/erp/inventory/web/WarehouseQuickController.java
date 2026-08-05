package com.averpo.erp.inventory.web;

import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Combobox «+ Янги қўшиш» қуйма endpoint'лари - омбор
 * (spec: docs/modules/combobox.md, Arbitr-066).
 *
 * <p>Алоҳида controller, чунки {@link WarehouseController} /settings
 * остида (INVENTORY соҳаси) - қуйма қўшиш эса ҳужжат формаларидан
 * INVENTORY EDIT эгаларига керак (user-roles.md соҳа қоидаси). Мантиқ бутунлай
 * {@link WarehouseService#create} реюзи (BR-WH-001/002 ўша ерда).
 */
@Controller
@RequiredArgsConstructor
public class WarehouseQuickController {

    /** Омборлар service - қуйма яратишда валидацияси реюз қилинади. */
    private final WarehouseService warehouseService;

    /** Модал ичи учун мини форма fragment'и (CSRF token билан). */
    @GetMapping("/warehouses/quick-form")
    public String quickForm() {
        return "inventory/warehouseQuickForm";
    }

    /**
     * Қуйма яратиш: ном + ихтиёрий код (омбор каталог формаси билан бир хил
     * минимал тўплам).
     *
     * @return муваффақиятда {id, label}; бизнес қоида бузилса 422 {message}
     */
    @PostMapping("/warehouses/quick")
    @ResponseBody
    public ResponseEntity<Map<String, String>> quick(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code) {
        try {
            Warehouse warehouse = warehouseService.create(name, code);
            return ResponseEntity.ok(Map.of(
                    "id", warehouse.getId().toString(),
                    "label", warehouse.getName()));
        } catch (BusinessRuleException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", e.displayMessage()));
        }
    }
}
