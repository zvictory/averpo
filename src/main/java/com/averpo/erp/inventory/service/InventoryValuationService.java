package com.averpo.erp.inventory.service;

import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.TrialBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory valuation ҳисоботи (QBO Inventory Valuation Summary +
 * бизнинг multi-warehouse кенгайтма, docs/modules/reports.md).
 *
 * <p>«Санага» қиймат stock_movement агрегатидан ТИКЛАНАДИ (StockBalance
 * фақат жорий ҳолат): кирим +qty/+қиймат, чиқим тескари. Landed cost
 * ҳиссалари movement ёзмайди - улар {@link LandedValueContribution}
 * порти орқали қўшилади. Жами қиймат GL'даги INVENTORY тизим счёти
 * қолдиғи билан солиштирилади: ҳар омбор ҳаракати GL билан бир
 * транзакцияда ёзилгани учун мос келиши ШАРТ - фарқ фақат счётга
 * қўлда JE/opening balance ёзилганида чиқади (ҳисобот огоҳлантиради).
 *
 * @author Zafar
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryValuationService {

    /**
     * Битта (item, омбор) кесими. Номлар controller'да бойитилади
     * (item бошқа модул - қоида №6, ItemService орқали).
     *
     * @param itemId      товар id'си
     * @param warehouseId омбор id'си
     * @param qty         asOf ҳолатига қолдиқ миқдор
     * @param value       asOf ҳолатига қиймат (home валютада)
     */
    public record Row(UUID itemId, UUID warehouseId, BigDecimal qty, BigDecimal value) { }

    /**
     * Тайёр ҳисобот.
     *
     * @param asOf ҳисобот санаси
     * @param rows фильтрланган кесимлар (омбор фильтри қўлланган)
     * @param totalValue кўрсатилган сатрлар жамиси
     * @param companyValue БУТУН компания қиймати (фильтрсиз) - GL
     *        солиштируви фақат шу билан маъноли (GL'да омбор кесими йўқ)
     * @param glBalance INVENTORY тизим счётининг asOf'даги қолдиғи;
     *        ягона фаол postable счёт топилмаса null (солиштириб бўлмайди)
     * @param matchesGl companyValue == glBalance (glBalance бор бўлса)
     */
    public record Report(LocalDate asOf, List<Row> rows, BigDecimal totalValue,
                         BigDecimal companyValue, BigDecimal glBalance,
                         boolean matchesGl) { }

    /** SQL агрегат учун JdbcClient - Hibernate'ни четлаб ўтади. */
    private final JdbcClient jdbc;

    /** Landed cost каби ташқи қиймат ҳиссалари (порт, ихтиёрий). */
    private final ObjectProvider<LandedValueContribution> contributions;

    /** INVENTORY тизим счётини топиш - ledger public API. */
    private final AccountService accountService;

    /** GL қолдиғи (home) - ledger public API. */
    private final TrialBalanceService trialBalanceService;

    /** Ҳисоботни asOf санасига қуради; warehouseId берилса фильтр. */
    public Report build(LocalDate asOf, UUID warehouseId) {
        // 1. Ҳаракатлардан (item, омбор) кесимида миқдор/қиймат тиклаш
        Map<Key, Agg> byKey = new HashMap<>();
        jdbc.sql("""
                SELECT m.item_id, m.warehouse_id,
                       COALESCE(SUM(CASE WHEN m.type IN ('IN', 'ADJUST_IN', 'TRANSFER_IN')
                           THEN m.quantity ELSE -m.quantity END), 0) AS qty,
                       COALESCE(SUM(CASE WHEN m.type IN ('IN', 'ADJUST_IN', 'TRANSFER_IN')
                           THEN m.total_cost ELSE -m.total_cost END), 0) AS value
                FROM stock_movement m
                WHERE m.movement_date <= :asOf
                GROUP BY m.item_id, m.warehouse_id
                """)
                .param("asOf", asOf)
                .query(rs -> {
                    Key key = new Key(rs.getObject("item_id", UUID.class),
                            rs.getObject("warehouse_id", UUID.class));
                    byKey.put(key, new Agg(rs.getBigDecimal("qty"),
                            rs.getBigDecimal("value")));
                });

        // 2. Ташқи ҳиссалар (landed cost) - фақат қийматга, миқдорга эмас
        contributions.stream()
                .flatMap(provider -> provider.contributions(asOf).stream())
                .forEach(entry -> byKey.merge(
                        new Key(entry.itemId(), entry.warehouseId()),
                        new Agg(BigDecimal.ZERO, entry.amount()),
                        (a, b) -> new Agg(a.qty().add(b.qty()), a.value().add(b.value()))));

        // 3. Компания жамиси (фильтрсиз) - GL солиштируви учун
        BigDecimal companyValue = byKey.values().stream().map(Agg::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Фильтр + бўш (ноль миқдор ВА ноль қиймат) кесимларни яшириш
        List<Row> rows = new ArrayList<>();
        byKey.forEach((key, agg) -> {
            if (warehouseId != null && !warehouseId.equals(key.warehouseId())) {
                return;
            }
            if (agg.qty().signum() == 0 && agg.value().signum() == 0) {
                return;
            }
            rows.add(new Row(key.itemId(), key.warehouseId(), agg.qty(), agg.value()));
        });
        BigDecimal totalValue = rows.stream().map(Row::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. GL солиштируви: INVENTORY тизим счёти (ягона бўлмаса null)
        BigDecimal glBalance = accountService
                .findSystemAccount(AccountDetailType.INVENTORY)
                .map(account -> trialBalanceService.balancesByAccountId(asOf)
                        .getOrDefault(account.getId(), BigDecimal.ZERO))
                .orElse(null);

        return new Report(asOf, rows, totalValue, companyValue, glBalance,
                glBalance != null && companyValue.compareTo(glBalance) == 0);
    }

    /** Map калити: (item, омбор) жуфти. */
    private record Key(UUID itemId, UUID warehouseId) { }

    /** Оралиқ агрегат: миқдор + қиймат. */
    private record Agg(BigDecimal qty, BigDecimal value) { }
}
