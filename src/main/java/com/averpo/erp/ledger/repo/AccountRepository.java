package com.averpo.erp.ledger.repo;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Счётлар репозиторийси - фақат ledger модули ичида ишлатилади.
 *
 * @author Zafar
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Ном unique (QBO услуби) - импорт/валидацияда шу орқали топилади. */
    Optional<Account> findByName(String name);

    /** Яратишда ном бандлигини текшириш. */
    boolean existsByName(String name);

    /** Код киритилган счётлар орасида unique текшируви. */
    boolean existsByCode(String code);

    /** Update'да код бошқа счётда бандлигини аниқлаш учун. */
    Optional<Account> findByCode(String code);

    /**
     * Тизим счётларини топиш йўли: код эмас, detail type (QBO услуби).
     * Масалан UNDEPOSITED_FUNDS ёки EXCHANGE_GAIN_OR_LOSS ягона счёти.
     */
    List<Account> findByDetailType(AccountDetailType detailType);

    /** Проводка формасидаги счёт select'и учун. */
    List<Account> findByActiveTrueAndPostableTrue();
}
