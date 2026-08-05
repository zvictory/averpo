package com.averpo.erp.contact.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Контактнинг масъул шахси (old-erp-ideas §3). Контактда биттагина
 * primary - ux_contact_person_primary partial unique, алмашув
 * ContactService'да. Таҳрирлаш MVP'да йўқ - ўчириб қайта қўшилади.
 */
@Entity
@Table(name = "contact_person")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactPerson extends BaseEntity {

    /** Эгаси. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    /** Тўлиқ исм - мажбурий (BR-CON-008). */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** Лавозим. */
    @Column(length = 100)
    private String position;

    /** Телефон. */
    @Column(length = 50)
    private String phone;

    /** Email - формати BR-CON-004 билан текширилади (service). */
    @Column
    private String email;

    /** Асосий шахс белгиси - контактда биттагина. */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    /** Янги шахс (валидация service'да). */
    public ContactPerson(Contact contact, String fullName, String position,
                         String phone, String email, boolean primary) {
        this.contact = contact;
        this.fullName = fullName;
        this.position = position;
        this.phone = phone;
        this.email = email;
        this.primary = primary;
    }

    /** Янги primary келганда эскисини бўшатиш учун (фақат service чақиради). */
    public void clearPrimary() { this.primary = false; }
}
