package com.averpo.erp.contact;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.repo.ContactRepository;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Контактлар CRUD валидациялари - spec: docs/modules/contact.md.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContactServiceTest {

    @Autowired ContactService contactService;

    /** Legacy (валютасиз) контактни apply'дан ўтмасдан яратиш учун. */
    @Autowired ContactRepository contactRepository;

    /** Home валюта кодини қаттиқ кодламай олиш учун (Arbitr-159 тестлари). */
    @Autowired CompanySettingsService settingsService;

    /** POSTED ҳужжат яратиш учун (BR-CON-012 қулф тести). */
    @Autowired com.averpo.erp.sales.service.InvoiceService invoiceService;
    @Autowired com.averpo.erp.item.service.ItemService itemService;
    @Autowired com.averpo.erp.ledger.service.AccountService accountService;

    /** Фақат ном билан минимал форма маълумоти. */
    private static ContactData data(String displayName) {
        return new ContactData(displayName, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** Ном + валюта билан форма маълумоти (қулф тестлари учун). */
    private static ContactData data(String displayName, String currency) {
        return new ContactData(displayName, null, null, null, null,
                null, currency, null, null, null, null);
    }

    @Test
    void create_setsTypeAndName() {
        Contact customer = contactService.create(ContactType.CUSTOMER, data("Алишер савдо"));
        assertThat(customer.getType()).isEqualTo(ContactType.CUSTOMER);
        assertThat(customer.getDisplayName()).isEqualTo("Алишер савдо");
        assertThat(customer.isActive()).isTrue();
    }

    @Test
    void displayName_uniqueAcrossTypes() {
        // QBO namespace қоидаси: customer ва vendor бир хил ном ололмайди
        contactService.create(ContactType.CUSTOMER, data("Глобал трейд"));

        assertThatThrownBy(() ->
                contactService.create(ContactType.VENDOR, data("Глобал трейд")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("банд");
    }

    @Test
    void currency_unknownRejected_validAccepted() {
        assertThatThrownBy(() -> contactService.create(ContactType.VENDOR,
                new ContactData("Импортёр", null, null, null, null,
                        null, "XXX", null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("каталогда йўқ");

        Contact vendor = contactService.create(ContactType.VENDOR,
                new ContactData("Импортёр", null, null, null, null,
                        null, "usd", null, null, null, null));
        // Кичик ҳарф киритилса ҳам нормализация қилиниб каталогга боғланади
        assertThat(vendor.getCurrency()).isNotNull();
        assertThat(vendor.getCurrency().getCode()).isEqualTo("USD");
    }

    @Test
    void email_invalidRejected() {
        assertThatThrownBy(() -> contactService.create(ContactType.CUSTOMER,
                new ContactData("Почта тест", null, null, null, "нотўғри-почта",
                        null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void byType_filtersTypeAndInactive() {
        contactService.create(ContactType.CUSTOMER, data("Харидор А"));
        Contact hidden = contactService.create(ContactType.CUSTOMER, data("Харидор Б"));
        contactService.create(ContactType.VENDOR, data("Етказувчи В"));
        contactService.update(hidden.getId(), data("Харидор Б"), false);

        assertThat(contactService.byType(ContactType.CUSTOMER, false))
                .extracting(Contact::getDisplayName)
                .contains("Харидор А")
                .doesNotContain("Харидор Б", "Етказувчи В");
        assertThat(contactService.byType(ContactType.CUSTOMER, true))
                .extracting(Contact::getDisplayName)
                .contains("Харидор А", "Харидор Б");
    }

    @Test
    void update_keepOwnName_allowed() {
        Contact contact = contactService.create(ContactType.CUSTOMER, data("Ўз номи"));
        Contact updated = contactService.update(contact.getId(), data("Ўз номи"), true);
        assertThat(updated.getDisplayName()).isEqualTo("Ўз номи");
    }

    // ---- Arbitr-159: контакт валютаси server'да ҳеч қачон null (coalesce) ----

    /**
     * create бўш валюта → home сақланади (null ЭМАС). Server гарови:
     * quick-add/API/эски форма валютани юбормаса ҳам null ёзилмайди.
     */
    @Test
    void create_blankCurrency_savesHome() {
        Contact contact = contactService.create(ContactType.CUSTOMER, data("Бўш валюта мижоз"));
        assertThat(contact.getCurrency()).isNotNull();
        assertThat(contact.getCurrency().getCode()).isEqualTo(settingsService.homeCurrency());
    }

    /**
     * create конкрет чет валюта → ўша валюта (home'га алмаштирилмайди).
     * USD - каталогда фаол чет валюта (EUR 004-currency.sql'да нофаол).
     */
    @Test
    void create_explicitCurrency_savesIt() {
        Contact contact = contactService.create(ContactType.CUSTOMER,
                data("Конкрет валюта мижоз", "USD"));
        assertThat(contact.getCurrency().getCode()).isEqualTo("USD");
    }

    /**
     * АСОСИЙ хавфсизлик тести: update бўш input эски ЧЕТ ВАЛЮТАЛИ (USD)
     * контактни home'га РЕСЕТЛАМАЙДИ - мавжуд валюта сақланади (coalesce
     * мавжудни устувор олади). Наив «бўш → home» ечим бу ерда бузиларди.
     */
    @Test
    void update_blankInput_keepsExistingCurrency_notReset() {
        Contact contact = contactService.create(ContactType.CUSTOMER,
                data("Ресет тест мижоз", "USD"));
        assertThat(contact.getCurrency().getCode()).isEqualTo("USD");

        Contact updated = contactService.update(contact.getId(),
                data("Ресет тест мижоз", null), true);
        assertThat(updated.getCurrency()).isNotNull();
        assertThat(updated.getCurrency().getCode()).isEqualTo("USD");
    }

    /**
     * update бўш input, мавжуд контакт валютаси null (legacy маълумот) →
     * home (миграция). Legacy контакт apply'дан ўтмай яратилади
     * (159'дан олдин ContactService null сақлар эди).
     */
    @Test
    void update_blankInput_legacyNullCurrency_migratesToHome() {
        Contact legacy = contactRepository.saveAndFlush(
                new Contact(ContactType.CUSTOMER, "Легаси null мижоз"));
        assertThat(legacy.getCurrency()).isNull();

        Contact updated = contactService.update(legacy.getId(),
                data("Легаси null мижоз", null), true);
        assertThat(updated.getCurrency()).isNotNull();
        assertThat(updated.getCurrency().getCode()).isEqualTo(settingsService.homeCurrency());
    }

    /** update конкрет input → ўша валюта ёзилади (087 lock йўқ пайтда эркин). */
    @Test
    void update_explicitInput_savesIt() {
        Contact contact = contactService.create(ContactType.CUSTOMER, data("Таҳрир валюта мижоз"));
        Contact updated = contactService.update(contact.getId(),
                data("Таҳрир валюта мижоз", "USD"), true);
        assertThat(updated.getCurrency().getCode()).isEqualTo("USD");
    }

    /** Кутилган BR-CON-012 рад ёрдамчиси (қулф тестлари учун). */
    private void assertLockRejected(java.util.UUID contactId, ContactData update) {
        assertThatThrownBy(() -> contactService.update(contactId, update, true))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-CON-012"));
    }

    /**
     * Контакт учун home валютада POSTED invoice яратади - қулфни
     * фаоллаштиради (валюта контактдан келади, 087). item номи контакт
     * номи билан бетакрор (seed grep тузоғи - ном такрори BR берарди).
     */
    private void postHomeInvoice(Contact customer) {
        var defaults = itemService.defaultsFor(com.averpo.erp.item.domain.ItemType.SERVICE);
        var item = itemService.create(com.averpo.erp.item.domain.ItemType.SERVICE,
                new com.averpo.erp.item.service.ItemService.ItemData(
                        "Қулф хизмати " + customer.getDisplayName(), null, null, null, null, null,
                        defaults.income(), null, null, defaults.expense(), null, null));
        var draft = invoiceService.createDraft(
                new com.averpo.erp.sales.service.InvoiceService.InvoiceData(
                        customer.getId(), java.time.LocalDate.of(2026, 7, 8), null,
                        null, null, null,
                        java.util.List.of(new com.averpo.erp.sales.service.InvoiceService.LineData(
                                item.getId(), null, java.math.BigDecimal.ONE,
                                new java.math.BigDecimal("1000"), null, null))));
        invoiceService.post(draft.getId());
    }

    /**
     * BR-CON-012 (Arbitr-087) қулф семантикаси Arbitr-159 coalesce билан
     * САҚЛАНАДИ: POSTED ҳужжат йўқ - валюта эркин; POSTED бор -
     * қиймат→бошқа қиймат РАД. Coalesce нозиклиги: бўш input мавжудни
     * сақлайди (ресет ЙЎҚ) - POSTED бўлса ҳам «ўзгармаган» деб ўтади,
     * value→null тақиқи ишга тушмайди (159: null умуман сақланмайди).
     */
    @Test
    void currencyLock_freeWithoutPosted_valueToValueRejectedWithPosted() {
        Contact customer = contactService.create(ContactType.CUSTOMER, data("Қулф мижози", "USD"));

        // POSTED ҳужжат йўқ - валюта эркин ўзгаради (қиймат→қиймат)
        contactService.update(customer.getId(), data("Қулф мижози", settingsService.homeCurrency()), true);
        // бўш input мавжудни сақлайди (159 coalesce - ресет ЙЎҚ), POSTED
        // йўқ - қулф халақит бермайди
        Contact afterBlank = contactService.update(customer.getId(), data("Қулф мижози", null), true);
        assertThat(afterBlank.getCurrency().getCode()).isEqualTo(settingsService.homeCurrency());
        assertThat(contactService.isCurrencyLocked(contactService.get(customer.getId()))).isFalse();

        // Home валютада POSTED invoice - контакт энди тарихли (валюта home)
        accountService.importDefaultChart();
        postHomeInvoice(customer);
        assertThat(contactService.isCurrencyLocked(contactService.get(customer.getId()))).isTrue();

        // қиймат → бошқа қиймат (home → USD), POSTED бор - РАД
        assertLockRejected(customer.getId(), data("Қулф мижози", "USD"));

        // бўш input POSTED'да ҳам ресетламайди - coalesce мавжуд home'ни
        // сақлайди, қулф уни «ўзгармаган» деб ўтказади
        Contact blankLocked = contactService.update(customer.getId(), data("Қулф мижози", null), true);
        assertThat(blankLocked.getCurrency().getCode()).isEqualTo(settingsService.homeCurrency());

        // Валюта ўзгармаган оддий таҳрир бемалол ўтади (қулф халақит бермайди)
        Contact same = contactService.update(customer.getId(),
                data("Қулф мижози", settingsService.homeCurrency()), true);
        assertThat(same.getCurrency().getCode()).isEqualTo(settingsService.homeCurrency());
    }

    /**
     * BR-CON-012 first-fill (087 ўтиш қоидаси, legacy контакт): валютаси
     * null контакт POSTED ҳужжатларда қатнашган бўлса, БИРИНЧИ тўлдириш
     * фақат ўша ҳужжатлар валютасига мос бўлса ўтади - зид қиймат РАД.
     * Coalesce'да legacy null'ни бўш input home'га кўчиради (POSTED
     * home'га мос → ўтади).
     */
    @Test
    void currencyLock_legacyNullFirstFillWithPosted() {
        accountService.importDefaultChart();

        // Legacy null контакт + home валютада POSTED invoice
        Contact rejectCase = contactRepository.saveAndFlush(
                new Contact(ContactType.CUSTOMER, "Легаси зид мижоз"));
        postHomeInvoice(rejectCase);
        // null → зид қиймат (POSTED home, USD зид) - РАД
        assertLockRejected(rejectCase.getId(), data("Легаси зид мижоз", "USD"));

        // Бошқа legacy null контакт: бўш input home'га кўчади (POSTED
        // home'га мос) - first-fill ЎТАДИ, миграция бажарилади
        Contact migrateCase = contactRepository.saveAndFlush(
                new Contact(ContactType.CUSTOMER, "Легаси мос мижоз"));
        postHomeInvoice(migrateCase);
        Contact migrated = contactService.update(migrateCase.getId(),
                data("Легаси мос мижоз", null), true);
        assertThat(migrated.getCurrency().getCode()).isEqualTo(settingsService.homeCurrency());
    }
}
