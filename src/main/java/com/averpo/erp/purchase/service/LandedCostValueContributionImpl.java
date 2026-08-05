package com.averpo.erp.purchase.service;

import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.LandedValueContribution;
import com.averpo.erp.purchase.domain.LandedCostAllocation;
import com.averpo.erp.purchase.domain.LandedCostAllocationLine;
import com.averpo.erp.purchase.repo.LandedCostAllocationLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Landed cost'нинг inventory valuation ҳиссаси (порт имплементацияси):
 * тақсимот қаторларининг омбор қийматига қўшилган улуши
 * (inventory_share - COGS улуши омбор қийматига кирмайди).
 *
 * <p>«Кучда» талқини: allocation_date &lt;= asOf ВА (POSTED ёки
 * reversal_date &gt; asOf). Сторно санаси йўқ REVERSED (бўлиши мумкин
 * эмас - migration тўлдиради) эҳтиёткорлик учун чиқариб ташланади.
 *
 * @author Zafar
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LandedCostValueContributionImpl implements LandedValueContribution {

    /** Тақсимот қаторлари - ўз модул repo'си. */
    private final LandedCostAllocationLineRepository lineRepository;

    /** Receipt'нинг item/омборини аниқлаш - inventory public API. */
    private final InventoryService inventoryService;

    /** {@inheritDoc} */
    @Override
    public List<Entry> contributions(LocalDate asOf) {
        List<Entry> result = new ArrayList<>();
        for (LandedCostAllocationLine line
                : lineRepository.findByAllocationAllocationDateLessThanEqual(asOf)) {
            LandedCostAllocation allocation = line.getAllocation();
            boolean active = allocation.getStatus() == LandedCostAllocation.Status.POSTED
                    || (allocation.getReversalDate() != null
                        && allocation.getReversalDate().isAfter(asOf));
            if (!active || line.getInventoryShare().signum() == 0) {
                continue;
            }
            StockMovement receipt = inventoryService.movement(line.getMovementId());
            result.add(new Entry(receipt.getItemId(),
                    receipt.getWarehouse().getId(), line.getInventoryShare()));
        }
        return result;
    }
}
