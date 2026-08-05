package com.averpo.erp.audit.repo;

import com.averpo.erp.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Аудит ёзувлари репозиторийси - ФАҚАТ audit модули ичида (қоида №6).
 *
 * <p>Append-only инвариант: ёзиш AuditLogService.record орқали,
 * update/delete методлари эълон қилинмайди (JpaRepository'нинг
 * delete'лари бор, лекин уларни ҲЕЧ КИМ чақирмайди - service қатъий
 * ёзиш-ўқиш API бериб қўяди). Филтрли рўйхат Specification билан -
 * ихтиёрий (сана, тур, username) комбинациялар null-параметр SQL
 * муаммосисиз ишлайди.
 *
 * @author Zafar
 */
public interface AuditEventRepository
        extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    /** Rollback исботи тести учун: шу JE'га ёзув қолган-қолмаганини айтади. */
    boolean existsByEntryId(UUID entryId);
}
