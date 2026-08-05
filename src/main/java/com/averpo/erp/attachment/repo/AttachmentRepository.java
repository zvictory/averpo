package com.averpo.erp.attachment.repo;

import com.averpo.erp.attachment.domain.Attachment;
import com.averpo.erp.shared.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Attachment репозиторийси - фақат attachment модули ичида ишлатилади.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /** Ҳужжат иловалари, янгидан эскига (кўриш экрани «Иловалар» бўлими). */
    List<Attachment> findByDocumentTypeAndDocumentIdOrderByCreatedAtDesc(
            DocumentType documentType, UUID documentId);
}
