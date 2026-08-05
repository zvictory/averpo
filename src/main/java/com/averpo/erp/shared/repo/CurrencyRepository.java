package com.averpo.erp.shared.repo;

import com.averpo.erp.shared.domain.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Валюта каталоги репозиторийси - фақат shared модул ичида ишлатилади.
 *
 * @author Zafar
 */
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {

    /** ISO код бўйича қидириш - валидация ва импортда ишлатилади. */
    Optional<Currency> findByCode(String code);

    /** Ҳужжат формаларидаги валюта select'и учун фаоллар рўйхати. */
    List<Currency> findByActiveTrueOrderByCode();

    /** Созламалар экранидаги тўлиқ рўйхат. */
    List<Currency> findAllByOrderByCode();
}
