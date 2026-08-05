package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Тўлов шарти каталоги - QBO'даги Terms рўйхати (Net 30, Due on receipt).
 *
 * <p>Shared'да туради (Currency паттерни), чунки контактдан ташқари
 * Invoice/Bill ҳам кейинги босқичларда тўғридан-тўғри ишлатади.
 * Due date = ҳужжат санаси + days; AR/AP aging шунга таянади.
 *
 * @author Zafar
 */
@Entity
@Table(name = "payment_term")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTerm extends BaseEntity {

    /** Кўрсатиладиган ном: «Due on receipt», «Net 30» - unique. */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Ҳужжат санасидан неча кунда тўланади (0 - дарҳол). */
    @Column(nullable = false)
    private int days;

    /** Нофаол term янги ҳужжатларда танланмайди. */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги term - каталогга қўшишда. */
    public PaymentTerm(String name, int days, boolean active) {
        this.name = name;
        this.days = days;
        this.active = active;
    }

    /** Ном/кунни янгилаш. */
    public void update(String name, int days, boolean active) {
        this.name = name;
        this.days = days;
        this.active = active;
    }
}
