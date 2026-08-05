package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.PaymentMethod;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.PaymentMethodService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тўлов усуллари каталоги тестлари (Arbitr-033): CRUD + seed +
 * BR кодисиз валидация чегаралари (NotFound, DB unique дубли,
 * нофаол усул select'дан тушиб қолиши).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentMethodServiceTest {

    @Autowired PaymentMethodService paymentMethodService;

    @Test
    void seed_containsThreeDefaultMethods() {
        // Changeset 036 seed'i: учта усул тайёр келади
        assertThat(paymentMethodService.all())
                .extracting(PaymentMethod::getName)
                .contains("Нақд", "Банк ўтказмаси", "Пластик карта");
    }

    @Test
    void createUpdate_andInactiveDropsFromSelect() {
        PaymentMethod method = paymentMethodService.create("  QR тўлов  ");
        // Ном strip қилинади
        assertThat(method.getName()).isEqualTo("QR тўлов");
        assertThat(paymentMethodService.activeForSelect())
                .extracting(PaymentMethod::getName).contains("QR тўлов");

        // Нофаол қилинса ҳужжат select'идан тушади, all()'да қолади
        paymentMethodService.update(method.getId(), "QR тўлов", false);
        assertThat(paymentMethodService.activeForSelect())
                .extracting(PaymentMethod::getName).doesNotContain("QR тўлов");
        assertThat(paymentMethodService.all())
                .extracting(PaymentMethod::getName).contains("QR тўлов");
    }

    @Test
    void duplicateName_rejectedByDbUnique() {
        // BR кодисиз (арбитр кўлами): дубль DB unique'дан келади,
        // controller уни flash хабарига айлантиради
        assertThatThrownBy(() -> paymentMethodService.create("Нақд"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void get_unknownId_notFound() {
        assertThatThrownBy(() -> paymentMethodService.get(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
