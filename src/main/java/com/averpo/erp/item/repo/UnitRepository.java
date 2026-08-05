package com.averpo.erp.item.repo;

import com.averpo.erp.item.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ўлчов бирликлари репозиторийси.
 */
public interface UnitRepository extends JpaRepository<Unit, UUID> {

    /** Ном unique - валидация учун. */
    Optional<Unit> findByName(String name);

    /** Item формасидаги select учун - фаоллар. */
    List<Unit> findByActiveTrueOrderByName();

    /** Созламалар экрани учун - ҳаммаси. */
    List<Unit> findAllByOrderByName();

    /** Гуруҳ бирликлари (base биринчи, кейин ном) - инвариант ва select'лар учун. */
    List<Unit> findByGroupIdOrderByBaseDescNameAsc(UUID groupId);

    /** Гуруҳдаги фаол бирликлар - ҳужжат сатри select'и учун. */
    List<Unit> findByGroupIdAndActiveTrueOrderByBaseDescNameAsc(UUID groupId);
}
