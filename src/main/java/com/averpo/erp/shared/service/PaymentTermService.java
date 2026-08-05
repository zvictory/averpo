package com.averpo.erp.shared.service;

import com.averpo.erp.shared.domain.PaymentTerm;
import com.averpo.erp.shared.repo.PaymentTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Тўлов шартлари каталогининг public API'си - contact (ва кейин
 * sales/purchase) модуллари шу орқали мурожаат қилади.
 *
 * @author Zafar
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentTermService {

    /** Тўлов шартлари репозиторийси. */
    private final PaymentTermRepository repository;

    /** Формалардаги select учун фаоллар, кун тартибида. */
    public List<PaymentTerm> active() {
        return repository.findByActiveTrueOrderByDays();
    }

    /** Id мавжудлигини текширади - контакт формаси валидацияси. */
    public boolean exists(UUID id) {
        return id != null && repository.existsById(id);
    }

    /** Id бўйича шарт - Bill/Invoice due date ҳисоби учун (бўш бўлиши мумкин). */
    public java.util.Optional<PaymentTerm> byId(UUID id) {
        return id == null ? java.util.Optional.empty() : repository.findById(id);
    }
}
