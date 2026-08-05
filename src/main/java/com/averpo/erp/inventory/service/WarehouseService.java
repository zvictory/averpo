package com.averpo.erp.inventory.service;

import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.repo.WarehouseRepository;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Омборлар CRUD - бошқа модуллар омборга фақат шу public service
 * орқали мурожаат қилади (ТЕМИР ҚОИДА №6). Ўчириш йўқ - фақат
 * active=false (ҳаракатлар тарихи бузилмайди).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WarehouseService {

    /** Омборлар репозиторийси. */
    private final WarehouseRepository repository;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Warehouse get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Омбор топилмади: " + id));
    }

    /**
     * Сўралган id'лар бўйича омборлар битта IN сўровда (Arbitr-045
     * findAllById нақши) - ҳужжат service'лари сатр-циклда {@link #get}'сиз
     * мавжудликни текшириши учун (SalesReceipt Beruniy-035). Топилмаганлар
     * рўйхатда бўлмайди; мавжудликни чақирувчи текширади.
     */
    @Transactional(readOnly = true)
    public List<Warehouse> findAllById(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findAllById(ids);
    }

    /** Рўйхат экрани учун - ҳаммаси, ном тартибида. */
    @Transactional(readOnly = true)
    public List<Warehouse> all() {
        return repository.findAllByOrderByName();
    }

    /**
     * Каталог рўйхати филтри (Arbitr-068, list-filters.md): active -
     * TRUE фақат фаол / FALSE фақат нофаол / null ҳаммаси; q - ном/код
     * contains (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(Boolean active, String q) {
    }

    /**
     * Рўйхат экрани - тўлиқ филтр (Arbitr-068): фаоллик/матн битта
     * Specification'да (audit услуби, ListSpecs бўлаклари), ном тартибида.
     */
    @Transactional(readOnly = true)
    public List<Warehouse> list(ListFilter filter) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.eq("active", filter.active()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "name", "code")),
                org.springframework.data.domain.Sort.by("name"));
    }

    /** Формалардаги select учун фаол омборлар. */
    @Transactional(readOnly = true)
    public List<Warehouse> active() {
        return repository.findByActiveTrueOrderByName();
    }

    /**
     * Янги омбор яратади.
     *
     * @throws BusinessRuleException BR-WH-001 (ном бўш/банд),
     *         BR-WH-002 (код банд)
     */
    public Warehouse create(String name, String code) {
        String normalizedName = requireName(name, null);
        String normalizedCode = requireCodeFree(code, null);
        return repository.save(new Warehouse(normalizedName, normalizedCode));
    }

    /** Омборни янгилайди (ном/код/фаоллик). */
    public Warehouse update(UUID id, String name, String code, boolean active) {
        Warehouse warehouse = get(id);
        String normalizedName = requireName(name, id);
        String normalizedCode = requireCodeFree(code, id);
        warehouse.update(normalizedName, normalizedCode, active);
        return warehouse;
    }

    /** BR-WH-001: ном бўш эмас ва unique (ўзиникидан ташқари). */
    private String requireName(String name, UUID selfId) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_WH_001,
                    "Омбор номи киритилиши шарт");
        }
        String normalized = name.strip();
        repository.findByName(normalized)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_WH_001,
                            "Бу ном банд: " + normalized);
                });
        return normalized;
    }

    /** BR-WH-002: код киритилса unique (ўзиникидан ташқари); бўш → null. */
    private String requireCodeFree(String code, UUID selfId) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.strip().toUpperCase();
        repository.findByCode(normalized)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_WH_002,
                            "Бу код банд: " + normalized);
                });
        return normalized;
    }
}
