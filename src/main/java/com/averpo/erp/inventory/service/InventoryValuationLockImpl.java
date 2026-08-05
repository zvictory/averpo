package com.averpo.erp.inventory.service;

import com.averpo.erp.inventory.repo.StockMovementRepository;
import com.averpo.erp.shared.service.InventoryValuationLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InventoryValuationLock} портининг импли (BR-SET-003):
 * биттагина омбор ҳаракати пайдо бўлиши билан valuation методи
 * ўзгартирилмайди - мавжуд таннарх ҳисоблари маъносини йўқотар эди
 * (home currency қулфи паттерни). Package-private: ташқарига фақат
 * shared'даги порт кўринади.
 *
 * @author Zafar
 */
@Component
@RequiredArgsConstructor
class InventoryValuationLockImpl implements InventoryValuationLock {

    /** Ҳаракатлар мавжудлигини текшириш учун. */
    private final StockMovementRepository movementRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean locked() {
        return movementRepository.count() > 0;
    }
}
