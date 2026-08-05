package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Тўлов усули каталоги (Arbitr-033, QBO PaymentMethod): Нақд, Банк
 * ўтказмаси, Пластик карта... Ҳужжатда (bank_transaction) FK билан
 * туради - усул ЎЧИРИЛМАЙДИ, фақат нофаол қилинади (каталог қолипи:
 * тарихий ҳужжат изи сақланади). QBO'даги Type майдони атайлаб йўқ -
 * credit card кўлами рад этилган (Otabek-001).
 */
@Entity
@Table(name = "payment_method")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod extends BaseEntity {

    /** Кўрсатиладиган ном (unique) - QBO Name (бизда 30 белги). */
    @Column(nullable = false, length = 30)
    private String name;

    /** Нофаол усул янги ҳужжат select'ида кўринмайди. */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги усул - фаол ҳолда яратилади. */
    public PaymentMethod(String name) {
        this.name = name;
    }

    /** Таҳрир: ном ва фаоллик (PaymentMethodService чақиради). */
    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }
}
