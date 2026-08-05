package com.averpo.erp.contact.repo;

import com.averpo.erp.contact.domain.ContactBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Контакт банк реквизитлари репозиторийси - фақат contact модули ичида.
 *
 * @author Zafar
 */
public interface ContactBankAccountRepository extends JpaRepository<ContactBankAccount, UUID> {

    /** Контактнинг барча реквизитлари - киритилиш тартибида. */
    List<ContactBankAccount> findByContactIdOrderByCreatedAtAsc(UUID contactId);

    /** Ҳисоб рақами контакт ичида unique текшируви учун (BR-CON-010). */
    boolean existsByContactIdAndAccountNumber(UUID contactId, String accountNumber);

    /** Жорий default реквизит - янги default келганда бўшатилади. */
    List<ContactBankAccount> findByContactIdAndDefaultAccountTrue(UUID contactId);
}
