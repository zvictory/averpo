package com.averpo.erp.contact.repo;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService.ContactRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Контактлар репозиторийси - фақат contact модули ичида ишлатилади.
 * JpaSpecificationExecutor - каталог рўйхати филтри учун (DEC-068).
 */
public interface ContactRepository extends JpaRepository<Contact, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Contact> {

    /** Display name глобал unique - валидация учун. */
    Optional<Contact> findByDisplayName(String displayName);

    /** Рўйхат экрани: тип бўйича, фаоллар. */
    List<Contact> findByTypeAndActiveTrueOrderByDisplayName(ContactType type);

    /** Рўйхат экрани: тип бўйича, ҳаммаси (nofaollar тогли ёқилганда). */
    List<Contact> findByTypeOrderByDisplayName(ContactType type);

    /** ИНН uniqueness текшируви учун (BR-CON-005). */
    Optional<Contact> findByTaxId(String taxId);

    /** Енгил ссылкалар фақат сўралган id'лар учун (PERF-018). */
    @Query("""
            select new com.averpo.erp.contact.service.ContactService$ContactRef(
                c.id, c.displayName)
            from Contact c
            where c.id in :ids
            """)
    List<ContactRef> findRefsByIdIn(@Param("ids") Collection<UUID> ids);

    /** Тур бўйича фаол контактларнинг енгил рўйхати (PERF-018). */
    @Query("""
            select new com.averpo.erp.contact.service.ContactService$ContactRef(
                c.id, c.displayName)
            from Contact c
            where c.type = :type and c.active = true
            order by c.displayName
            """)
    List<ContactRef> findActiveRefsByType(@Param("type") ContactType type);
}
