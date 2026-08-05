package com.averpo.erp.contact.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.web.FormParsers;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Контакт яратиш/таҳрирлаш формаси. Ҳамма майдон String - хато бўлганда
 * фойдаланувчи киритган қийматлар йўқолмасдан қайта кўрсатилади
 * (AccountForm паттерни).
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class ContactForm {

    /** Глобал unique кўрсатиладиган ном. */
    private String displayName;

    /** Юридик/компания номи. */
    private String companyName;

    /** Масъул шахс исми. */
    private String firstName;

    /** Масъул шахс фамилияси. */
    private String lastName;

    /** Электрон почта. */
    private String email;

    /** Телефон. */
    private String phone;

    /** Валюта ISO коди ёки бўш (home currency). */
    private String currency;

    /** Тўлов шарти id'си (UUID матн кўринишида) ёки бўш. */
    private String paymentTermId;

    /** ИНН (ихтиёрий, киритилса глобал unique - BR-CON-005). */
    private String taxId;

    /** Кредит лимити - фақат CUSTOMER формасида кўрсатилади (матн,
     * минг ажратгичли киритишга чидамли - BR-CON-006 билан парсланади). */
    private String creditLimit;

    /** Ойлик oklad - фақат EMPLOYEE формасида кўрсатилади (матн, минг
     * ажратгичли киритишга чидамли - BR-CON-011 билан парсланади). */
    private String monthlySalary;

    /** Эркин изоҳ. */
    private String notes;

    /** Фаоллик - фақат таҳрирда кўрсатилади. */
    private boolean active = true;

    /** Таҳрир формаси - мавжуд контактдан тўлдирилади. */
    public static ContactForm from(Contact contact) {
        ContactForm form = new ContactForm();
        form.displayName = contact.getDisplayName();
        form.companyName = contact.getCompanyName();
        form.firstName = contact.getFirstName();
        form.lastName = contact.getLastName();
        form.email = contact.getEmail();
        form.phone = contact.getPhone();
        form.currency = contact.getCurrency() == null
                ? null : contact.getCurrency().getCode();
        form.paymentTermId = contact.getPaymentTermId() == null
                ? null : contact.getPaymentTermId().toString();
        form.taxId = contact.getTaxId();
        form.creditLimit = contact.getCreditLimit() == null
                ? null : contact.getCreditLimit().stripTrailingZeros().toPlainString();
        form.monthlySalary = contact.getMonthlySalary() == null
                ? null : contact.getMonthlySalary().stripTrailingZeros().toPlainString();
        form.notes = contact.getNotes();
        form.active = contact.isActive();
        return form;
    }

    /**
     * Service қабул қиладиган кўринишга айлантиради. Parse қоидаси
     * FormParsers'да: бузуқ тўлов шарти танлови BR-CON-003, бузуқ credit
     * limit BR-CON-006 билан формага қайтади (хом exception эмас).
     */
    public ContactService.ContactData toData() {
        return new ContactService.ContactData(displayName, companyName,
                firstName, lastName, email, phone, currency,
                FormParsers.uuid(paymentTermId, BusinessRule.BR_CON_003, "Тўлов шарти"),
                taxId,
                FormParsers.decimal(creditLimit, BusinessRule.BR_CON_006, "Credit limit"),
                FormParsers.decimal(monthlySalary, BusinessRule.BR_CON_011, "Ойлик oklad"),
                notes);
    }
}
