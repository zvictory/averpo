package com.averpo.erp.contact;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
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
 * EMPLOYEE (Payroll 23а) контакт CRUD ва oklad валидацияси - spec:
 * docs/modules/payroll.md «Contact кенгайтма». Мавжуд contact
 * инфратузилмаси қайта ишлатилади (ContactType.EMPLOYEE + monthly_salary).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeContactTest {

    @Autowired ContactService contactService;

    /** Ходим формаси: ном + ойлик oklad (12-майдонли канон конструктор). */
    private static ContactData emp(String displayName, BigDecimal oklad) {
        return new ContactData(displayName, null, null, null, null,
                null, null, null, null, null, oklad, null);
    }

    @Test
    void create_employee_persistsTypeAndOklad() {
        Contact e = contactService.create(ContactType.EMPLOYEE,
                emp("Ходим Азиз", new BigDecimal("5000000")));
        assertThat(e.getType()).isEqualTo(ContactType.EMPLOYEE);
        assertThat(e.getDisplayName()).isEqualTo("Ходим Азиз");
        assertThat(e.getMonthlySalary()).isEqualByComparingTo("5000000");
        assertThat(e.isActive()).isTrue();
    }

    @Test
    void update_employee_changesOklad() {
        Contact e = contactService.create(ContactType.EMPLOYEE,
                emp("Ходим Бекзод", new BigDecimal("3000000")));
        contactService.update(e.getId(), emp("Ходим Бекзод", new BigDecimal("3500000")), true);

        Contact reloaded = contactService.get(e.getId());
        assertThat(reloaded.getMonthlySalary()).isEqualByComparingTo("3500000");
    }

    @Test
    void byType_employee_listsOnlyEmployees() {
        contactService.create(ContactType.EMPLOYEE, emp("Ходим Дилноза", null));
        contactService.create(ContactType.CUSTOMER, emp("Мижоз Санжар", null));

        assertThat(contactService.byType(ContactType.EMPLOYEE, false))
                .extracting(Contact::getDisplayName)
                .contains("Ходим Дилноза")
                .doesNotContain("Мижоз Санжар");
    }

    @Test
    void oklad_onNonEmployee_rejectedCon011() {
        // BR-CON-011: oklad фақат EMPLOYEE учун (credit_limit/BR-CON-006 симметрияси)
        assertThatThrownBy(() -> contactService.create(ContactType.CUSTOMER,
                emp("Мижоз оклад билан", new BigDecimal("100000"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-011"));
    }

    @Test
    void oklad_negative_rejectedCon011() {
        assertThatThrownBy(() -> contactService.create(ContactType.EMPLOYEE,
                emp("Ходим манфий", new BigDecimal("-1"))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-011"));
    }
}
