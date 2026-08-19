package com.averpo.erp.shared.service;

import com.averpo.erp.shared.domain.PaymentMethod;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.repo.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Тўлов усуллари каталогининг ягона public API'си (DEC-033).
 * Бошқа модуллар (bank) фақат шу орқали мурожаат қилади (қоида №6).
 *
 * <p>Янги BR коди атайлаб ЙЎҚ (лойиҳа кўлами): мавжудлик NotFound
 * билан, нофаоллик select даражасида (нофаол усул формада умуман
 * кўринмайди), ном дубли DB unique constraint билан ушланади -
 * controller DataIntegrityViolation'ни тушунарли flash хабарига
 * айлантиради. Delete ЙЎҚ - каталог қолипи (тарихий ҳужжат FK изи).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentMethodService {

    /** Усуллар репозиторийси. */
    private final PaymentMethodRepository repository;

    /** Созламалар экрани - барчаси (нофаоллар билан), ном тартибида. */
    @Transactional(readOnly = true)
    public List<PaymentMethod> all() {
        return repository.findAllByOrderByName();
    }

    /** Ҳужжат формаси select'и - фақат фаоллар. */
    @Transactional(readOnly = true)
    public List<PaymentMethod> activeForSelect() {
        return repository.findByActiveTrueOrderByName();
    }

    /** Id бўйича топади ёки тушунарли хато отади (ҳужжат валидацияси ҳам шу). */
    @Transactional(readOnly = true)
    public PaymentMethod get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тўлов усули топилмади: " + id));
    }

    /**
     * Янги усул. saveAndFlush - ном дубли (uq_payment_method_name)
     * чақирувчига шу ернинг ўзида DataIntegrityViolation бўлиб қайтсин
     * (commit'га қолдирилса controller'нинг catch'идан ўтиб кетарди).
     */
    public PaymentMethod create(String name) {
        return repository.saveAndFlush(new PaymentMethod(name.strip()));
    }

    /** Таҳрир: ном ва фаоллик (дубл текшируви create билан бир хил - DB unique). */
    public PaymentMethod update(UUID id, String name, boolean active) {
        PaymentMethod method = get(id);
        method.update(name.strip(), active);
        repository.saveAndFlush(method);
        return method;
    }
}
