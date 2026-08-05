package com.averpo.erp.item.repo;

import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService.ItemRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Item репозиторийси - фақат item модули ичида ишлатилади.
 *
 * <p>Рўйхат query'ларида category/unit ҳам fetch қилинади
 * (open-in-view=false, шаблонда lazy хатоси бўлмасин).
 *
 * @author Zafar
 */
public interface ItemRepository extends JpaRepository<Item, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Item> {

    /** Ном unique - валидация учун. */
    Optional<Item> findByName(String name);

    /** SKU киритилган бўлса unique - валидация учун. */
    Optional<Item> findBySku(String sku);

    /**
     * Енгил ссылкалар фақат сўралган id'лар учун (Beruniy-018) - номлар
     * entity ва унинг EAGER боғларисиз, битта IN сўровда.
     */
    @Query("""
            select new com.averpo.erp.item.service.ItemService$ItemRef(
                i.id, i.name, u.name)
            from Item i
            left join i.unit u
            where i.id in :ids
            """)
    List<ItemRef> findRefsByIdIn(@Param("ids") Collection<UUID> ids);

    /** Фаол item'ларнинг енгил рўйхати - select учун (Beruniy-018). */
    @Query("""
            select new com.averpo.erp.item.service.ItemService$ItemRef(
                i.id, i.name, u.name)
            from Item i
            left join i.unit u
            where i.active = true
            order by i.name
            """)
    List<ItemRef> findActiveRefs();
}
