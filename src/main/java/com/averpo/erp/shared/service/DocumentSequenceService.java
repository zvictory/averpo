package com.averpo.erp.shared.service;

import com.averpo.erp.shared.domain.DocumentSequence;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.repo.DocumentSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Умумий ҳужжат рақамлаш public API'си (docs/modules/document-sequence.md).
 * Барча модуллар (ledger, кейин sales/purchases) ҳужжат рақамини фақат
 * шу орқали олади - формат ва race ҳимояси битта жойда.
 */
@Service
@RequiredArgsConstructor
public class DocumentSequenceService {

    /** Sequence қаторлари - қулфлаб ўқиш учун. */
    private final DocumentSequenceRepository repository;

    /**
     * Навбатдаги ҳужжат рақамини ажратади: {@code INV-2026-00001}.
     *
     * <p>{@code MANDATORY}: рақам ҳужжат яратилаётган транзакция ИЧИДА
     * олиниши шарт - ҳужжат сақланмай қолса рақам ҳам rollback бўлади,
     * рақамлар кетма-кетлигида тешик қолмайди. Транзакциясиз чақириш
     * дастурчи хатоси сифатида дарҳол йиқилади.
     *
     * @param type         ҳужжат тури (JE/INV/BILL/PAY)
     * @param documentDate ҳужжат санаси - рақамдаги йил шундан олинади,
     *                     рақамнинг ўзи йилга боғлиқ эмас (узилмайди)
     * @throws BusinessRuleException BR-SEQ-001 - тур учун sequence
     *         қатори йўқ (seed changeset тушмаган deploy хатоси)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type, LocalDate documentDate) {
        DocumentSequence sequence = repository.lockByDocumentType(type)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_SEQ_001,
                        "Рақамлаш созламаси топилмади: " + type
                        + " - administrator'га мурожаат қилинг (seed changeset 014)"));
        return sequence.allocate(documentDate.getYear());
    }
}
