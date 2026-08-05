package com.averpo.erp.item.service;

import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.item.domain.ItemCategory;
import com.averpo.erp.item.repo.ItemCategoryRepository;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Товар категориялари CRUD - Account иерархияси паттернлари
 * (DFS дарахт, цикл ҳимояси) қайта ишлатилади.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ItemCategoryService {

    /** Дарахт тугуни: категория + чуқурлик (indent учун). */
    public record CategoryNode(ItemCategory category, int depth) { }

    /**
     * Таҳрир формаси учун текис кўриниш - parent LAZY бўлгани сабаб
     * (open-in-view=false) ота id'си транзакция ичида ечиб берилади.
     */
    public record CategoryEdit(UUID id, String name, UUID parentId, boolean active) { }

    /** Таҳрир формасига категория маълумотлари. */
    @Transactional(readOnly = true)
    public CategoryEdit editView(UUID id) {
        ItemCategory category = get(id);
        return new CategoryEdit(category.getId(), category.getName(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.isActive());
    }

    /** Категориялар репозиторийси. */
    private final ItemCategoryRepository repository;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public ItemCategory get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория топилмади: " + id));
    }

    /** Барча категориялар - item формасидаги select учун. */
    @Transactional(readOnly = true)
    public List<ItemCategory> all() {
        return repository.findAllByOrderByName();
    }

    /** DFS тартибидаги текис дарахт - рўйхат экрани учун. */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<ItemCategory> all = repository.findAllByOrderByName();
        Map<UUID, List<ItemCategory>> children = new HashMap<>();
        List<ItemCategory> roots = new ArrayList<>();
        for (ItemCategory category : all) {
            if (category.getParent() == null) {
                roots.add(category);
            } else {
                children.computeIfAbsent(category.getParent().getId(),
                        k -> new ArrayList<>()).add(category);
            }
        }
        List<CategoryNode> result = new ArrayList<>(all.size());
        for (ItemCategory root : roots) {
            flatten(root, 0, children, result);
        }
        return result;
    }

    /** DFS ёрдамчиси. */
    private void flatten(ItemCategory category, int depth,
                         Map<UUID, List<ItemCategory>> children, List<CategoryNode> out) {
        out.add(new CategoryNode(category, depth));
        for (ItemCategory child : children.getOrDefault(category.getId(), List.of())) {
            flatten(child, depth + 1, children, out);
        }
    }

    /** Янги категория яратади. */
    public ItemCategory create(String name, UUID parentId) {
        requireFreeName(name, null);
        ItemCategory parent = parentId == null ? null : get(parentId);
        return repository.save(new ItemCategory(name.strip(), parent));
    }

    /** Категорияни янгилайди - цикл ҳимояси билан. */
    public ItemCategory update(UUID id, String name, UUID parentId, boolean active) {
        ItemCategory category = get(id);
        requireFreeName(name, id);
        ItemCategory parent = parentId == null ? null : get(parentId);
        requireNoCycle(category, parent);
        category.update(name.strip(), parent);
        category.setActive(active);
        return category;
    }

    /** Ном бандлигини текширади. */
    private void requireFreeName(String name, UUID selfId) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_CAT_001, "Категория номи бўш бўлиши мумкин эмас");
        }
        repository.findByName(name.strip())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_CAT_001, "Бу ном банд: " + name.strip());
                });
    }

    /** Иерархия цикл ҳимояси - Account'даги паттерн. */
    private void requireNoCycle(ItemCategory category, ItemCategory newParent) {
        ItemCategory cursor = newParent;
        int guard = 0;
        while (cursor != null) {
            if (cursor.getId().equals(category.getId())) {
                throw new BusinessRuleException(BusinessRule.BR_CAT_002, "Иерархияда цикл: «" + category.getName()
                        + "» ўз шажарасидаги категорияга ота бўла олмайди");
            }
            if (++guard > 100) {
                throw new BusinessRuleException(BusinessRule.BR_CAT_002, "Категория иерархияси жуда чуқур ёки бузилган");
            }
            cursor = cursor.getParent();
        }
    }
}
