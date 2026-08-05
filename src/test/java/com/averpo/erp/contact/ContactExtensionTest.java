package com.averpo.erp.contact;

import com.averpo.erp.contact.domain.AddressType;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactAddress;
import com.averpo.erp.contact.domain.ContactBankAccount;
import com.averpo.erp.contact.domain.ContactPerson;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.AddressData;
import com.averpo.erp.contact.service.ContactService.BankAccountData;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.contact.service.ContactService.PersonData;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contact кенгайтмаси тестлари (docs/modules/contact.md, «Кенгайтма»):
 * ИНН, credit limit, манзил/шахс/банк реквизити ва default/primary
 * алмашув мантиғи.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContactExtensionTest {

    @Autowired ContactService contactService;

    /** Минимал контакт маълумоти - ном, ИНН, credit limit билан. */
    private static ContactData data(String displayName, String taxId, BigDecimal creditLimit) {
        return new ContactData(displayName, null, null, null, null,
                null, null, null, taxId, creditLimit, null);
    }

    /** Тайёр customer яратади. */
    private Contact customer(String name) {
        return contactService.create(ContactType.CUSTOMER, data(name, null, null));
    }

    // ---- ИНН (BR-CON-005) ----

    @Test
    void taxId_duplicateAcrossTypes_rejected() {
        contactService.create(ContactType.CUSTOMER, data("ИНН эгаси", "123456789", null));

        // Vendor'да ҳам ўша ИНН тақиқ - каталог глобал
        assertThatThrownBy(() -> contactService.create(ContactType.VENDOR,
                data("ИНН дубликат", "123456789", null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-005"));
    }

    @Test
    void taxId_nullOnManyContacts_allowed_andSelfUpdateKeepsOwn() {
        // NULL ИНН чекланмайди - partial unique WHERE tax_id IS NOT NULL
        customer("ИННсиз А");
        customer("ИННсиз Б");

        // Ўз ИННини сақлаб қолиш - муаммосиз
        Contact owner = contactService.create(ContactType.CUSTOMER,
                data("ИНН ўзиники", "987654321", null));
        Contact updated = contactService.update(owner.getId(),
                data("ИНН ўзиники", "987654321", null), true);
        assertThat(updated.getTaxId()).isEqualTo("987654321");
    }

    // ---- Credit limit (BR-CON-006) ----

    @Test
    void creditLimit_onVendorOrNegative_rejected() {
        assertThatThrownBy(() -> contactService.create(ContactType.VENDOR,
                data("Лимитли етказувчи", null, new BigDecimal("1000000"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-006"));

        assertThatThrownBy(() -> contactService.create(ContactType.CUSTOMER,
                data("Манфий лимит", null, new BigDecimal("-1"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-006"));

        Contact ok = contactService.create(ContactType.CUSTOMER,
                data("Лимитли харидор", null, new BigDecimal("25000000")));
        assertThat(ok.getCreditLimit()).isEqualByComparingTo("25000000");
    }

    // ---- Манзиллар (BR-CON-007) ----

    @Test
    void address_firstBecomesDefault_newDefaultSwitches() {
        Contact contact = customer("Манзилли мижоз");

        // Биринчи манзил default сўралмаса ҳам автоматик default бўлади
        ContactAddress first = contactService.addAddress(contact.getId(),
                new AddressData(AddressType.BILLING, "Тошкент, Амир Темур 1",
                        null, "Тошкент", null, "100000", "uz", false));
        assertThat(first.isDefaultAddress()).isTrue();
        assertThat(first.getCountryCode()).isEqualTo("UZ");

        // Иккинчиси default деб келса - биринчиси бўшатилади
        ContactAddress second = contactService.addAddress(contact.getId(),
                new AddressData(AddressType.BILLING, "Тошкент, Навоий 10",
                        null, null, null, null, null, true));
        assertThat(second.isDefaultAddress()).isTrue();
        assertThat(contactService.addresses(contact.getId()))
                .filteredOn(ContactAddress::isDefaultAddress)
                .hasSize(1)
                .first()
                .extracting(ContactAddress::getAddressLine1)
                .isEqualTo("Тошкент, Навоий 10");

        // Бошқа турдаги default мустақил
        ContactAddress shipping = contactService.addAddress(contact.getId(),
                new AddressData(AddressType.SHIPPING, "Омбор манзили",
                        null, null, null, null, null, false));
        assertThat(shipping.isDefaultAddress()).isTrue();
    }

    @Test
    void address_blankLine1_rejected() {
        Contact contact = customer("Бўш манзил");

        assertThatThrownBy(() -> contactService.addAddress(contact.getId(),
                new AddressData(AddressType.BILLING, "  ",
                        null, null, null, null, null, false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-007"));
    }

    // ---- Шахслар (BR-CON-008) ----

    @Test
    void person_blankName_rejected_primarySwitches() {
        Contact contact = customer("Шахсли мижоз");

        assertThatThrownBy(() -> contactService.addPerson(contact.getId(),
                new PersonData(" ", null, null, null, false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-008"));

        ContactPerson first = contactService.addPerson(contact.getId(),
                new PersonData("Алиев Вали", "Директор", null, null, false));
        assertThat(first.isPrimary()).isTrue();

        contactService.addPerson(contact.getId(),
                new PersonData("Каримов Салим", "Ҳисобчи", null, null, true));
        assertThat(contactService.persons(contact.getId()))
                .filteredOn(ContactPerson::isPrimary)
                .hasSize(1)
                .first()
                .extracting(ContactPerson::getFullName)
                .isEqualTo("Каримов Салим");
    }

    @Test
    void person_invalidEmail_rejected() {
        Contact contact = customer("Шахс почта тест");

        assertThatThrownBy(() -> contactService.addPerson(contact.getId(),
                new PersonData("Тестов Тест", null, null, "бузуқ почта", false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-004"));
    }

    // ---- Банк реквизитлари (BR-CON-009/010) ----

    @Test
    void bankAccount_requiredFields_andDuplicateNumber() {
        Contact contact = customer("Банкли мижоз");

        assertThatThrownBy(() -> contactService.addBankAccount(contact.getId(),
                new BankAccountData(" ", null, "2020 8000 1234", null, false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-009"));

        ContactBankAccount first = contactService.addBankAccount(contact.getId(),
                new BankAccountData("Капиталбанк", "01088", "20208000123456789001", "USD", false));
        assertThat(first.isDefaultAccount()).isTrue();
        assertThat(first.getCurrency().getCode()).isEqualTo("USD");

        // Шу контактда ўша рақам - тақиқ
        assertThatThrownBy(() -> contactService.addBankAccount(contact.getId(),
                new BankAccountData("Бошқа банк", null, "20208000123456789001", null, false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-010"));

        // Бошқа контактда ўша рақам - муаммосиз
        Contact other = customer("Бошқа мижоз");
        ContactBankAccount otherAccount = contactService.addBankAccount(other.getId(),
                new BankAccountData("Капиталбанк", null, "20208000123456789001", null, false));
        assertThat(otherAccount.getAccountNumber()).isEqualTo("20208000123456789001");
    }

    @Test
    void children_deletable() {
        Contact contact = customer("Ўчириш тести");
        ContactAddress address = contactService.addAddress(contact.getId(),
                new AddressData(AddressType.LEGAL, "Юридик манзил",
                        null, null, null, null, null, false));
        ContactPerson person = contactService.addPerson(contact.getId(),
                new PersonData("Вақтинчалик Шахс", null, null, null, false));
        ContactBankAccount bank = contactService.addBankAccount(contact.getId(),
                new BankAccountData("Банк", null, "12345", null, false));

        contactService.deleteAddress(address.getId());
        contactService.deletePerson(person.getId());
        contactService.deleteBankAccount(bank.getId());

        assertThat(contactService.addresses(contact.getId())).isEmpty();
        assertThat(contactService.persons(contact.getId())).isEmpty();
        assertThat(contactService.bankAccounts(contact.getId())).isEmpty();
    }
}
