package com.averpo.erp.contact.repo;

import com.averpo.erp.contact.domain.ContactPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Контакт шахслари репозиторийси - фақат contact модули ичида.
 */
public interface ContactPersonRepository extends JpaRepository<ContactPerson, UUID> {

    /** Контактнинг барча шахслари - киритилиш тартибида. */
    List<ContactPerson> findByContactIdOrderByCreatedAtAsc(UUID contactId);

    /** Жорий primary шахс - янги primary келганда бўшатилади. */
    List<ContactPerson> findByContactIdAndPrimaryTrue(UUID contactId);
}
