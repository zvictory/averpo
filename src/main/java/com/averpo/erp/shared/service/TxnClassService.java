package com.averpo.erp.shared.service;

import com.averpo.erp.shared.domain.TxnClass;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.repo.TxnClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Йўналишлар (Class) каталогининг ягона public API'си
 * (docs/modules/class-tracking.md). Ledger posting'да фаолликни шу
 * орқали текширади (BR-CLS-001) - shared'да тургани учун қоида №6
 * бузилмайди. Delete API атайлаб ЙЎҚ - GL тарихида ишлатилган class
 * фақат нофаол қилинади.
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TxnClassService {

    /**
     * Экран/select учун тайёр кўриниш: тўлиқ ном «Ота:Бола» (QBO
     * FullyQualifiedName) + дарахтдаги чуқурлик (каталогда indent учун).
     */
    public record ClassOption(UUID id, String fullName, int depth, boolean active) { }

    /** Йўналишлар репозиторийси. */
    private final TxnClassRepository repository;

    /** Каталог экрани: дарахт тартибида, нофаоллар билан. */
    @Transactional(readOnly = true)
    public List<ClassOption> all() {
        return treeOptions(false);
    }

    /** Ҳужжат формалари select'и: дарахт тартибида, фақат фаоллар. */
    @Transactional(readOnly = true)
    public List<ClassOption> activeForSelect() {
        return treeOptions(true);
    }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public TxnClass get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Йўналиш топилмади: " + id));
    }

    /**
     * Ҳисобот устун сарлавҳалари учун id → тўлиқ ном («Ота:Бола»)
     * харитаси - нофаоллар ҳам киради (тарихий GL сатрлари изи).
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesById() {
        Map<UUID, String> names = new HashMap<>();
        for (TxnClass txnClass : repository.findAll()) {
            names.put(txnClass.getId(), txnClass.fullyQualifiedName());
        }
        return names;
    }

    /**
     * GL posting гарови (BR-CLS-001): танланган class мавжуд ва фаол
     * бўлиши шарт - post'да ҳар сафар қайта текширилади (draft'дан
     * кейин нофаол қилинган бўлиши мумкин, TaxRate BR-TAX-003 нақши).
     */
    @Transactional(readOnly = true)
    public void requireActive(UUID classId) {
        TxnClass txnClass = repository.findById(classId)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_CLS_001,
                        "Танланган йўналиш каталогда йўқ: " + classId));
        if (!txnClass.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_CLS_001,
                    "Нофаол йўналиш танланмайди: " + txnClass.fullyQualifiedName());
        }
    }

    /**
     * Янги йўналиш (ихтиёрий ота билан).
     *
     * @throws BusinessRuleException BR-CLS-002 - шу ота ичида ном банд
     */
    public TxnClass create(String name, UUID parentId) {
        TxnClass parent = parentId == null ? null : get(parentId);
        String normalized = requireUniqueName(name, parent, null);
        return repository.save(new TxnClass(normalized, parent));
    }

    /**
     * Номлаш (ота ўзгармайди - у {@link #changeParent} да).
     *
     * @throws BusinessRuleException BR-CLS-002 - шу ота ичида ном банд
     */
    public TxnClass rename(UUID id, String name) {
        TxnClass txnClass = get(id);
        txnClass.rename(requireUniqueName(name, txnClass.getParent(), id));
        return txnClass;
    }

    /**
     * Ота алмаштириш - BR-CLS-003: ўзи ёки ўз авлоди ота бўлолмайди
     * (цикл дарахтни бузади, fullyQualifiedName чексиз айланарди).
     *
     * @throws BusinessRuleException BR-CLS-002 (янги ота ичида ном банд),
     *         BR-CLS-003 (цикл)
     */
    public TxnClass changeParent(UUID id, UUID parentId) {
        TxnClass txnClass = get(id);
        UUID currentParentId = txnClass.getParent() == null
                ? null : txnClass.getParent().getId();
        if (java.util.Objects.equals(currentParentId, parentId)) {
            return txnClass; // ўзгариш йўқ
        }
        TxnClass parent = parentId == null ? null : get(parentId);
        for (TxnClass cursor = parent; cursor != null; cursor = cursor.getParent()) {
            if (cursor.getId().equals(id)) {
                throw new BusinessRuleException(BusinessRule.BR_CLS_003,
                        "Йўналиш ўзи ёки ўз авлодига кўчирилмайди: " + txnClass.getName());
            }
        }
        requireUniqueName(txnClass.getName(), parent, id);
        txnClass.changeParent(parent);
        return txnClass;
    }

    /** Фаоллаштириш/нофаол қилиш - нофаол class янги ҳужжатда танланмайди. */
    public TxnClass setActive(UUID id, boolean active) {
        TxnClass txnClass = get(id);
        txnClass.setActive(active);
        return txnClass;
    }

    // ---- ички ёрдамчилар ----

    /** BR-CLS-002: strip қилинган ном шу ота ичида (ўзидан бошқада) банд эмас. */
    private String requireUniqueName(String name, TxnClass parent, UUID selfId) {
        if (name == null || name.isBlank()) {
            // Алоҳида BR йўқ (форма required) - tampered бўш ном ҳам
            // ноёблик қоидасининг бузилиши сифатида қайтади
            throw new BusinessRuleException(BusinessRule.BR_CLS_002,
                    "Йўналиш номи бўш бўлмайди");
        }
        String normalized = name.strip();
        repository.findByParentAndName(parent, normalized)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_CLS_002,
                            "Бу ном банд: " + other.fullyQualifiedName());
                });
        return normalized;
    }

    /** Дарахт тартиби: илдизлар ном бўйича, ҳар тугун остида болалари. */
    private List<ClassOption> treeOptions(boolean activeOnly) {
        List<TxnClass> classes = repository.findAllByOrderByName();
        Map<UUID, List<TxnClass>> children = new HashMap<>();
        List<TxnClass> roots = new ArrayList<>();
        for (TxnClass txnClass : classes) {
            if (txnClass.getParent() == null) {
                roots.add(txnClass);
            } else {
                children.computeIfAbsent(txnClass.getParent().getId(),
                        k -> new ArrayList<>()).add(txnClass);
            }
        }
        List<ClassOption> options = new ArrayList<>();
        for (TxnClass root : roots) {
            appendSubtree(root, 0, "", children, options, activeOnly);
        }
        return options;
    }

    /** DFS: тўлиқ ном йўл бўйида йиғилади (N+1 parent юришисиз). */
    private void appendSubtree(TxnClass node, int depth, String prefix,
                               Map<UUID, List<TxnClass>> children,
                               List<ClassOption> options, boolean activeOnly) {
        String fullName = prefix.isEmpty() ? node.getName() : prefix + ":" + node.getName();
        if (!activeOnly || node.isActive()) {
            options.add(new ClassOption(node.getId(), fullName, depth, node.isActive()));
        }
        for (TxnClass child : children.getOrDefault(node.getId(), List.of())) {
            appendSubtree(child, depth + 1, fullName, children, options, activeOnly);
        }
    }
}
