package com.averpo.erp.shared.repo;

import com.averpo.erp.shared.domain.DocumentSequence;
import com.averpo.erp.shared.domain.DocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    /**
     * Тур бўйича sequence қаторини PESSIMISTIC_WRITE (SELECT ... FOR
     * UPDATE) билан олади - транзакция тугагунча бошқа транзакциялар
     * шу турга рақам ололмайди. Айнан шу қулф parallel икки ҳужжатга
     * бир хил рақам берилишининг олдини олади; optimistic retry ўрнига
     * қулф танланди, чунки рақамлаш қисқа ва тўқнашув кутилган ҳолат.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.documentType = :type")
    Optional<DocumentSequence> lockByDocumentType(DocumentType type);
}
