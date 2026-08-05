package com.averpo.erp.contact.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Контакт - харидор ёки етказиб берувчи (QBO Customer/Vendor).
 *
 * <p>Display name глобал unique (QBO namespace қоидаси: бир ном ҳам
 * customer ҳам vendor бўлолмайди). Контакт ўчирилмайди - фақат
 * inactive қилинади, тарихдаги ҳужжатлар бузилмасин.
 *
 * <p>PaymentTerm shared модулда бўлгани учун id орқали сақланади
 * (dimension паттерни эмас - shared'га JPA relation мумкин эди, лекин
 * id соддароқ ва list экранларида lazy муаммосиз).
 */
@Entity
@Table(name = "contact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contact extends BaseEntity {

    /** CUSTOMER ёки VENDOR - рўйхатларни ажратади. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContactType type;

    /** Глобал unique кўрсатиладиган ном - асосий идентификатор. */
    @Column(name = "display_name", nullable = false, unique = true)
    private String displayName;

    /** Юридик/компания номи. */
    @Column(name = "company_name")
    private String companyName;

    /** Масъул шахс исми. */
    @Column(name = "first_name", length = 100)
    private String firstName;

    /** Масъул шахс фамилияси. */
    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Электрон почта. */
    @Column
    private String email;

    /** Телефон. */
    @Column(length = 50)
    private String phone;

    /**
     * Контакт валютаси - Currency каталогига ManyToOne (QBO
     * multicurrency услуби). null - home currency. 7-босқичда биринчи
     * POSTED ҳужжатдан кейин қулфланади. EAGER - рўйхат шаблонида
     * lazy хатоси бўлмасин.
     */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinColumn(name = "currency_id")
    private com.averpo.erp.shared.domain.Currency currency;

    /** Тўлов шарти - PaymentTerm каталог id'си ёки null. */
    @Column(name = "payment_term_id")
    private UUID paymentTermId;

    /**
     * ИНН - киритилса глобал unique (BR-CON-005, DB'да partial unique
     * ux_contact_tax_id). Формат текширилмайди: хорижий контрагентлар
     * турли узунликдаги рақам киритиши мумкин.
     */
    @Column(name = "tax_id", length = 20)
    private String taxId;

    /**
     * Кредит лимити - фақат CUSTOMER, МИЖОЗ ВАЛЮТАСИДА (QBO услуби -
     * мижоз битта валютали, лимит шу валютада; InvoiceService.creditCheck
     * очиқ AR'ни конверсиясиз шунга солиштиради), манфий эмас
     * (BR-CON-006, DB CHECK ҳам бор). null - лимит йўқ.
     */
    @Column(name = "credit_limit", precision = 19, scale = 4)
    private java.math.BigDecimal creditLimit;

    /**
     * Ойлик oklad - фақат EMPLOYEE учун (PayrollRun сатрида prefill
     * қиймати; мажбурий эмас). home валютада (payroll фақат home -
     * BR-PYR-001). null - белгиланмаган.
     */
    @Column(name = "monthly_salary", precision = 19, scale = 4)
    private java.math.BigDecimal monthlySalary;

    /** Эркин изоҳ. */
    @Column(columnDefinition = "text")
    private String notes;

    /** QBO make inactive - ўчириш ўрнига. */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /** Янги контакт - асосий майдонлар билан. */
    public Contact(ContactType type, String displayName) {
        this.type = type;
        this.displayName = displayName;
    }

    /** Барча таҳрирланадиган майдонларни бир жойда янгилайди. */
    public void update(String displayName, String companyName, String firstName,
                       String lastName, String email, String phone,
                       com.averpo.erp.shared.domain.Currency currency,
                       UUID paymentTermId, String taxId,
                       java.math.BigDecimal creditLimit,
                       java.math.BigDecimal monthlySalary, String notes) {
        this.displayName = displayName;
        this.companyName = companyName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.currency = currency;
        this.paymentTermId = paymentTermId;
        this.taxId = taxId;
        this.creditLimit = creditLimit;
        this.monthlySalary = monthlySalary;
        this.notes = notes;
    }
}
