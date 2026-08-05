package com.averpo.erp.shared.repo;

import com.averpo.erp.shared.domain.TxnClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Йўналишлар репозиторийси - ташқарига фақат TxnClassService орқали.
 *
 * @author Zafar
 */
public interface TxnClassRepository extends JpaRepository<TxnClass, UUID> {

    /** Каталог экрани - ном тартибида (дарахт тартиби service'да йиғилади). */
    List<TxnClass> findAllByOrderByName();

    /** BR-CLS-002 текшируви: шу ота ичида шу ном (top-level - parent null). */
    Optional<TxnClass> findByParentAndName(TxnClass parent, String name);
}
