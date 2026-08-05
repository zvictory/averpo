package com.averpo.erp.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Омбор қийматига ТАШҚАРИДАН қўшилган ҳиссалар порти (inventory
 * valuation ҳисоботи учун). Landed cost stock_movement ёзмасдан
 * қиймат қўшади (docs/modules/purchases.md «Landed cost») - «санага»
 * қиймат тиклашда бу ҳиссаларсиз ҳисобот GL'дан ажралиб қолар эди.
 *
 * <p>Порт паттерни (HomeCurrencyLock/InventoryValuationLock каби):
 * интерфейс истеъмолчи модулда (inventory), имплементация ҳисса
 * берувчида (purchase - у inventory'га боғланиши мумкин, тескариси
 * тақиқ, қоида №6). ObjectProvider орқали йиғилади - purchase модули
 * бўлмаса ҳам ҳисобот ишлайверади.
 *
 * @author Zafar
 */
public interface LandedValueContribution {

    /**
     * Битта ҳисса: (item, омбор) кесимига қўшилган қиймат.
     *
     * @param itemId      товар id'си (dimension)
     * @param warehouseId омбор id'си
     * @param amount      қўшилган қиймат (home валютада, мусбат)
     */
    record Entry(UUID itemId, UUID warehouseId, BigDecimal amount) { }

    /**
     * asOf санасигача (шу кун билан) кучга кирган ва шу санада ҲАЛИ
     * КУЧДА бўлган ҳиссалар: сторно қилинганлари сторно санасидан
     * бошлаб ҳисобга кирмайди.
     */
    List<Entry> contributions(LocalDate asOf);
}
