package com.averpo.erp.contact.repo;

import com.averpo.erp.contact.domain.AddressType;
import com.averpo.erp.contact.domain.ContactAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Контакт манзиллари репозиторийси - фақат contact модули ичида.
 */
public interface ContactAddressRepository extends JpaRepository<ContactAddress, UUID> {

    /** Контактнинг барча манзиллари - тур, кейин киритилиш тартибида. */
    List<ContactAddress> findByContactIdOrderByAddressTypeAscCreatedAtAsc(UUID contactId);

    /** Турдаги жорий default'ни топиш - янги default келганда бўшатилади. */
    List<ContactAddress> findByContactIdAndAddressTypeAndDefaultAddressTrue(
            UUID contactId, AddressType addressType);
}
