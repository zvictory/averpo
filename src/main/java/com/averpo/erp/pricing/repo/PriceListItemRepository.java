package com.averpo.erp.pricing.repo;

import com.averpo.erp.pricing.domain.PriceListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Поғонали нархлар репозиторийси.
 *
 * @author Zafar
 */
public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

    /** Рўйхат поғоналари - экран учун (item кесимини controller гуруҳлайди). */
    List<PriceListItem> findByPriceListIdOrderByMinQuantityAsc(UUID priceListId);

    /** Битта item'нинг поғоналари - resolvePrice учун. */
    List<PriceListItem> findByPriceListIdAndItemIdOrderByMinQuantityAsc(
            UUID priceListId, UUID itemId);

    /** Поғона дубликати текшируви (BR-PL-005). */
    Optional<PriceListItem> findByPriceListIdAndItemIdAndMinQuantity(
            UUID priceListId, UUID itemId, java.math.BigDecimal minQuantity);
}
