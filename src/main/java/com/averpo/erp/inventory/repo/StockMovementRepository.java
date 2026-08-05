package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Омбор ҳаракатлари репозиторийси - фақат inventory модули ичида.
 * JpaSpecificationExecutor - Ҳаракатлар экрани мукаммал филтри учун
 * (Arbitr-093: тур/омбор/item/сана/ҳужжат; warehouse fetch spec ичида).
 */
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {

    /** Item тарихи экрани учун - янгидан эскига. */
    List<StockMovement> findByItemIdOrderByMovementDateDescCreatedAtDesc(UUID itemId);

    /** Манба ҳужжат ҳаракатлари (Bill post кирими → reverse қайтариши). */
    List<StockMovement> findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
            String referenceType, UUID referenceId);

    /**
     * Ҳаракатлар экрани - саҳифаланган, филтрсиз (Beruniy-perf1 2-босқич):
     * аввалги findTop100 функционал тешик эди (100-ёзувдан эскиси
     * УМУМАН кўринмасди). Тартиб Pageable sort'ида (InventoryService.
     * MOVEMENTS_SORT). Барча ёзувлар учун JpaRepository.findAll(Pageable).
     */

    /**
     * Landed cost формаси: охирги BILL киримлари (номзод receipt'лар).
     * warehouse LOAD граф билан - шаблон омбор номини кўрсатади
     * (open-in-view=false, lazy proxy шаблонда портлайди).
     */
    @EntityGraph(attributePaths = {"warehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    List<StockMovement> findTop100ByTypeAndReferenceTypeOrderByMovementDateDescCreatedAtDesc(
            com.averpo.erp.inventory.domain.MovementType type, String referenceType);

    /** Битта ҳаракат warehouse'и билан - кўриш экранлари учун. */
    @EntityGraph(attributePaths = {"warehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<StockMovement> findWithWarehouseById(UUID id);

    /**
     * Танланган ҳаракатлар warehouse'и билан битта IN сўровда
     * (Sanjar-007) - landed cost N receipt'ни биттадан
     * {@link #findWithWarehouseById} қилмасин.
     */
    @EntityGraph(attributePaths = {"warehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    List<StockMovement> findWithWarehouseByIdIn(Collection<UUID> ids);

    // Ҳаракатлар экрани саҳифа методлари (Arbitr-049, Beruniy-037):
    // warehouse/counterpartWarehouse LOAD граф билан - акс ҳолда
    // open-in-view=false'да шаблон lazy proxy'ни юклаб саҳифада 25×2
    // гача қўшимча SELECT қиларди (жадвал омбор ва «иккинчи омбор»
    // устунларини кўрсатади).

    /** Ҳаракатлар экрани, омбор филтри билан - саҳифаланган (Beruniy-perf1). */
    @EntityGraph(attributePaths = {"warehouse", "counterpartWarehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    Page<StockMovement> findByWarehouseId(UUID warehouseId, Pageable pageable);

    /** Ҳаракатлар экрани, item филтри билан (T9 drill-down) - саҳифаланган. */
    @EntityGraph(attributePaths = {"warehouse", "counterpartWarehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    Page<StockMovement> findByItemId(UUID itemId, Pageable pageable);

    /** Ҳаракатлар экрани, item + омбор филтри билан (T9) - саҳифаланган. */
    @EntityGraph(attributePaths = {"warehouse", "counterpartWarehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    Page<StockMovement> findByItemIdAndWarehouseId(
            UUID itemId, UUID warehouseId, Pageable pageable);

    /**
     * Ҳаракатлар экрани, филтрсиз - саҳифаланган (JpaRepository.findAll'ни
     * @EntityGraph билан override): warehouse/counterpartWarehouse битта
     * сўровда, филтрли методлар билан бир хил (Beruniy-037).
     */
    @Override
    @EntityGraph(attributePaths = {"warehouse", "counterpartWarehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    Page<StockMovement> findAll(Pageable pageable);

    /**
     * (Омбор, item)нинг берилган пайтдан КЕЙИН ёзилган ҳаракатлари -
     * BR-INV-010 инварианти («қиймат ортга қайтариш фақат кейин ҳаракат
     * бўлмаганда») ва AVCO'да receipt'дан қолган миқдорни баҳолаш учун.
     * Хронология created_at бўйича: movement_date backdate қилиниши
     * мумкин, valuation эса айнан ёзилиш тартибида қўлланган.
     */
    List<StockMovement> findByWarehouseIdAndItemIdAndCreatedAtAfter(
            UUID warehouseId, UUID itemId, java.time.Instant createdAt);

    /**
     * Юқоридагининг «тенг ҳам киради» варианти - anchor movement билан
     * бир хил created_at'ли ҳаракатлар ҳам олиниб, кейинлик UUIDv7 id
     * тартиби билан аниқланади (бир транзакцияда тез кетма-кет ёзилган
     * ҳаракатларда created_at айнан тенг бўлиб қолиши мумкин - флейк).
     */
    List<StockMovement> findByWarehouseIdAndItemIdAndCreatedAtGreaterThanEqual(
            UUID warehouseId, UUID itemId, java.time.Instant createdAt);
}
