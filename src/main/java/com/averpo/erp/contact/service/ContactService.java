package com.averpo.erp.contact.service;

import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.EmailFormat;
import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.contact.domain.AddressType;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactAddress;
import com.averpo.erp.contact.domain.ContactBankAccount;
import com.averpo.erp.contact.domain.ContactPerson;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.repo.ContactAddressRepository;
import com.averpo.erp.contact.repo.ContactBankAccountRepository;
import com.averpo.erp.contact.repo.ContactPersonRepository;
import com.averpo.erp.contact.repo.ContactRepository;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.PaymentTermService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Контактлар CRUD - бошқа модуллар (sales, purchase) контактга фақат
 * шу public service орқали мурожаат қилади (ТЕМИР ҚОИДА №6).
 *
 * <p>Кенгайтма (old-erp-ideas §3): манзиллар, шахслар ва банк
 * реквизитлари ҳам шу service орқали бошқарилади - child CRUD учун
 * алоҳида public service очилмайди. Default/primary алмашуви хато эмас:
 * янгиси белгиланса эскиси автоматик бўшатилади, турдаги биринчи қатор
 * автоматик default бўлади (QBO услуби, docs/modules/contact.md).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ContactService {

    /** Контактлар репозиторийси. */
    private final ContactRepository repository;

    /** Манзиллар репозиторийси. */
    private final ContactAddressRepository addressRepository;

    /** Шахслар репозиторийси. */
    private final ContactPersonRepository personRepository;

    /** Банк реквизитлари репозиторийси. */
    private final ContactBankAccountRepository bankAccountRepository;

    /** Валюта каталогининг public API'си (shared) - repo эмас (қоида №6). */
    private final CurrencyService currencyService;

    /** Тўлов шартлари каталогининг public API'си (shared). */
    private final PaymentTermService paymentTermService;

    /** Компания созламалари - home currency (контакт валютаси null = home). */
    private final com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /**
     * BR-CON-012 қулфи учун хом SQL - бошқа модул (sales/purchase)
     * жадвалларига repository орқали эмас, JdbcClient билан қаралади
     * (AttachmentService/LedgerDashboardService прецеденти, қоида №6).
     */
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    /** Битта контакт формаси маълумотлари - create/update учун умумий. */
    public record ContactData(String displayName, String companyName,
                              String firstName, String lastName, String email,
                              String phone, String currency, UUID paymentTermId,
                              String taxId, BigDecimal creditLimit,
                              BigDecimal monthlySalary, String notes) {
        /**
         * Эски 11-майдонли (oklad'сиз) чақирувчилар учун compat
         * конструктор: monthlySalary = null. Мавжуд модул/тестлар
         * (customer/vendor) контактни oklad'сиз яратади; фақат EMPLOYEE
         * формаси 12-майдонли канон конструкторни ишлатади.
         */
        public ContactData(String displayName, String companyName, String firstName,
                           String lastName, String email, String phone, String currency,
                           UUID paymentTermId, String taxId, BigDecimal creditLimit,
                           String notes) {
            this(displayName, companyName, firstName, lastName, email, phone, currency,
                    paymentTermId, taxId, creditLimit, null, notes);
        }
    }

    /** Битта манзил формаси маълумотлари. */
    public record AddressData(AddressType type, String line1, String line2,
                              String city, String region, String postalCode,
                              String countryCode, boolean defaultAddress) { }

    /** Битта шахс формаси маълумотлари. */
    public record PersonData(String fullName, String position, String phone,
                             String email, boolean primary) { }

    /** Битта банк реквизити формаси маълумотлари (currency - ISO код). */
    public record BankAccountData(String bankName, String bankCode,
                                  String accountNumber, String currency,
                                  boolean defaultAccount) { }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Contact get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Контакт топилмади: " + id));
    }

    /** Рўйхат экрани учун: тип бўйича, ихтиёрий нофаоллар билан. */
    @Transactional(readOnly = true)
    public List<Contact> byType(ContactType type, boolean includeInactive) {
        return includeInactive
                ? repository.findByTypeOrderByDisplayName(type)
                : repository.findByTypeAndActiveTrueOrderByDisplayName(type);
    }

    /**
     * Каталог рўйхати филтри (Arbitr-068, list-filters.md): тип мажбурий
     * (экран route'идан келади); active - TRUE фақат фаол / FALSE фақат
     * нофаол / null ҳаммаси; q - ном/компания contains (катта-кичик
     * фарқсиз, кирилл ҳам).
     */
    public record ListFilter(ContactType type, Boolean active, String q) {
    }

    /**
     * Рўйхат экрани - тўлиқ филтр (Arbitr-068): фаоллик/матн битта
     * Specification'да (audit услуби, ListSpecs бўлаклари), ном тартибида.
     */
    @Transactional(readOnly = true)
    public List<Contact> list(ListFilter filter) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.eq("type", filter.type()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("active", filter.active()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "displayName", "companyName")),
                org.springframework.data.domain.Sort.by("displayName"));
    }

    /**
     * Енгил контакт ссылкаси (id + кўрсатиладиган ном) - ном хариталари
     * ва select'лар учун; тўлиқ entity хотирага юкланмайди
     * (Beruniy-018 overfetch'га қарши).
     */
    public record ContactRef(UUID id, String displayName) { }

    /**
     * Сўралган id'ларнинг енгил ссылкалари битта IN сўровда.
     * Фаоллик филтрланмайди - тарихий ёзувларда нофаол контакт номи
     * ҳам кўрсатилиши керак.
     */
    @Transactional(readOnly = true)
    public List<ContactRef> refsByIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findRefsByIdIn(ids);
    }

    /**
     * id → displayName харитаси битта IN сўровда (ARBITR-105б,
     * Ulugbek-003 §1): view/рўйхат name-map'лари бутун каталогни
     * юкламасин. Топилмаган id харитада бўлмайди - чақирувчи
     * {@code getOrDefault} билан ўқийди; фаоллик филтрланмайди
     * (тарихий ҳужжатда нофаол контакт номи ҳам кўринади).
     */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> namesByIds(Collection<UUID> ids) {
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        for (ContactRef ref : refsByIds(ids)) {
            names.put(ref.id(), ref.displayName());
        }
        return names;
    }

    /**
     * Сўралган id'лар бўйича ТЎЛИҚ контактлар битта IN сўровда - чақирувчи
     * модул сатр-циклда get() қилмаслиги учун (N+1'дан қочиш; масалан
     * payroll ходим текшируви). Топилмаган id'лар рўйхатда бўлмайди
     * (get()'дан фарқли - throw йўқ; мавжудликни чақирувчи текширади).
     */
    @Transactional(readOnly = true)
    public List<Contact> findAllById(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findAllById(ids);
    }

    /** Тур бўйича фаол контактларнинг енгил рўйхати - select учун. */
    @Transactional(readOnly = true)
    public List<ContactRef> activeRefsByType(ContactType type) {
        return repository.findActiveRefsByType(type);
    }

    /**
     * Янги контакт яратади.
     *
     * @throws BusinessRuleException display name бўш/банд, валюта/term
     *         каталогда йўқ, email бузуқ, ИНН банд ёки credit limit
     *         нотўғри бўлса (BR-CON-001..006)
     */
    public Contact create(ContactType type, ContactData data) {
        validate(type, data, null);
        Contact contact = new Contact(type, data.displayName().strip());
        // Arbitr-159: бўш валюта null сақланмайди - янги контактга home
        // қўйилади (мавжуд контакт йўқ, coalesce'нинг create шохи)
        apply(contact, data, effectiveCurrencyCode(data.currency(), null));
        return repository.save(contact);
    }

    /**
     * Контактни янгилайди - тип ўзгармайди (QBO ҳам customer'ни
     * vendor'га айлантирмайди).
     */
    public Contact update(UUID id, ContactData data, boolean active) {
        Contact contact = get(id);
        validate(contact.getType(), data, id);
        // Arbitr-159: эффектив валюта коди (coalesce) БИТТА манбадан -
        // қулф ҲАМ, apply ҲАМ ўшани олади. Акс ҳолда қулф хом
        // data.currency()'га қараб blank input'ни адаштиради (мавжуд
        // валютани ресетлаб ёки value→null деб рад қилиб)
        String effectiveCurrency = effectiveCurrencyCode(data.currency(), contact);
        // Қулф фақат UPDATE'да маъноли (create'да POSTED тарих йўқ)
        enforceCurrencyLock(contact, effectiveCurrency);
        apply(contact, data, effectiveCurrency);
        contact.setActive(active);
        return contact;
    }

    /**
     * Контакт валютаси формада қулфланганми (BR-CON-012 кўриниш флаги):
     * currency қийматли ВА контакт POSTED ҳужжатларда қатнашган бўлса
     * true - select read-only + сабаб матни кўрсатилади. currency null
     * бўлса select очиқ қолади (биринчи тўлдиришга рухсат бор - зид
     * қийматни server барибир рад этади).
     */
    @Transactional(readOnly = true)
    public boolean isCurrencyLocked(Contact contact) {
        return contact.getCurrency() != null
                && !postedDocumentCurrencies(contact.getId()).isEmpty();
    }

    /**
     * AP/AR ҳужжат валютасининг ҳақиқат манбаи - КОНТАКТ (QBO қатъий,
     * Arbitr-087, docs/modules/multi-currency.md «Контакт валютаси»):
     * контакт валютаси null бўлса home currency коди, акс ҳолда
     * контактники. Ҳужжат servis'лари validate()'да шуни чақириб
     * қайтарилган кодни ҳужжатга ЁЗАДИ - client қийматига ишонилмайди;
     * client юборган {@code requested} бўш бўлмаса фақат мосликка
     * текширилади (tampered форма ушланади).
     *
     * @param contact   ҳужжат контакти (чақирувчи аллақачон юклаган)
     * @param requested форма юборган валюта коди (null/бўш бўлиши мумкин)
     * @param rule      мослик бузилганда отиладиган оилага хос BR код
     *                  (BR-SINV-011/BILL-013/RET-008/SR-004/EST-004/PO-004)
     * @return ҳужжатга ёзиладиган валюта коди (контактники ёки home)
     * @throws BusinessRuleException requested бўш эмас ва кутилганга
     *         тенг эмас бўлса
     */
    @Transactional(readOnly = true)
    public String requireDocumentCurrency(Contact contact, String requested, BusinessRule rule) {
        String expected = contact.getCurrency() == null
                ? settingsService.homeCurrency() : contact.getCurrency().getCode();
        if (requested != null && !requested.isBlank()
                && !expected.equals(requested.strip())) {
            throw new BusinessRuleException(rule,
                    "Ҳужжат валютаси (" + requested.strip() + ") «"
                    + contact.getDisplayName() + "» контакти валютасига ("
                    + expected + ") мос эмас - валюта контактдан келади");
        }
        return expected;
    }

    // ---- Манзиллар ----

    /** Контакт манзиллари - тур, кейин киритилиш тартибида. */
    @Transactional(readOnly = true)
    public List<ContactAddress> addresses(UUID contactId) {
        return addressRepository.findByContactIdOrderByAddressTypeAscCreatedAtAsc(contactId);
    }

    /**
     * Янги манзил қўшади. Турдаги биринчи манзил автоматик default;
     * default сўралган бўлса турдаги эски default бўшатилади.
     *
     * @throws BusinessRuleException BR-CON-007 - тур ёки 1-қатор бўш
     */
    public ContactAddress addAddress(UUID contactId, AddressData data) {
        Contact contact = get(contactId);
        if (data.type() == null || data.line1() == null || data.line1().isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_CON_007,
                    "Манзил тури ва биринчи қатор киритилиши шарт");
        }
        List<ContactAddress> currentDefaults = addressRepository
                .findByContactIdAndAddressTypeAndDefaultAddressTrue(contactId, data.type());
        boolean makeDefault = data.defaultAddress() || currentDefaults.isEmpty();
        if (data.defaultAddress()) {
            currentDefaults.forEach(ContactAddress::clearDefault);
            // Бўшатиш янги қатордан ОЛДИН базага етиб бориши шарт - акс
            // ҳолда partial unique index иккита default кўриб йиқилади
            addressRepository.flush();
        }
        // saveAndFlush: Persistable.isNew flush'гача true - шу транзакция
        // ичида дарҳол delete қилинса Spring Data «янги» деб жимгина
        // ўтказиб юборар эди; flush partial unique хатосини ҳам дарҳол тутади
        return addressRepository.saveAndFlush(new ContactAddress(contact, data.type(),
                data.line1().strip(), Strings.blankToNull(data.line2()),
                Strings.blankToNull(data.city()), Strings.blankToNull(data.region()),
                Strings.blankToNull(data.postalCode()), upperOrNull(data.countryCode()),
                makeDefault));
    }

    /** Манзилни ўчиради (MVP'да таҳрир йўқ - ўчириб қайта қўшилади). */
    public void deleteAddress(UUID addressId) {
        addressRepository.delete(addressRepository.findById(addressId)
                .orElseThrow(() -> new NotFoundException("Манзил топилмади: " + addressId)));
    }

    // ---- Шахслар ----

    /** Контакт шахслари - киритилиш тартибида. */
    @Transactional(readOnly = true)
    public List<ContactPerson> persons(UUID contactId) {
        return personRepository.findByContactIdOrderByCreatedAtAsc(contactId);
    }

    /**
     * Янги шахс қўшади. Биринчи шахс автоматик primary; primary
     * сўралган бўлса эскиси бўшатилади.
     *
     * @throws BusinessRuleException BR-CON-008 - исм бўш;
     *         BR-CON-004 - email формати бузуқ
     */
    public ContactPerson addPerson(UUID contactId, PersonData data) {
        Contact contact = get(contactId);
        if (data.fullName() == null || data.fullName().isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_CON_008,
                    "Шахснинг исми киритилиши шарт");
        }
        requireValidEmail(data.email());
        List<ContactPerson> currentPrimary = personRepository
                .findByContactIdAndPrimaryTrue(contactId);
        boolean makePrimary = data.primary() || currentPrimary.isEmpty();
        if (data.primary()) {
            currentPrimary.forEach(ContactPerson::clearPrimary);
            personRepository.flush();
        }
        // saveAndFlush - сабаби addAddress'даги изоҳда
        return personRepository.saveAndFlush(new ContactPerson(contact,
                data.fullName().strip(), Strings.blankToNull(data.position()),
                Strings.blankToNull(data.phone()), Strings.blankToNull(data.email()), makePrimary));
    }

    /** Шахсни ўчиради. */
    public void deletePerson(UUID personId) {
        personRepository.delete(personRepository.findById(personId)
                .orElseThrow(() -> new NotFoundException("Шахс топилмади: " + personId)));
    }

    // ---- Банк реквизитлари ----

    /** Контакт банк реквизитлари - киритилиш тартибида. */
    @Transactional(readOnly = true)
    public List<ContactBankAccount> bankAccounts(UUID contactId) {
        return bankAccountRepository.findByContactIdOrderByCreatedAtAsc(contactId);
    }

    /**
     * Янги банк реквизити қўшади. Биринчиси автоматик default; default
     * сўралган бўлса эскиси бўшатилади.
     *
     * @throws BusinessRuleException BR-CON-009 - банк номи/ҳисоб рақами
     *         бўш; BR-CON-010 - рақам шу контактда банд; BR-CUR-* -
     *         валюта каталог хатолари
     */
    public ContactBankAccount addBankAccount(UUID contactId, BankAccountData data) {
        Contact contact = get(contactId);
        if (data.bankName() == null || data.bankName().isBlank()
                || data.accountNumber() == null || data.accountNumber().isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_CON_009,
                    "Банк номи ва ҳисоб рақами киритилиши шарт");
        }
        String accountNumber = data.accountNumber().strip();
        if (bankAccountRepository.existsByContactIdAndAccountNumber(contactId, accountNumber)) {
            throw new BusinessRuleException(BusinessRule.BR_CON_010,
                    "Бу ҳисоб рақами шу контактда аллақачон бор: " + accountNumber);
        }
        List<ContactBankAccount> currentDefaults = bankAccountRepository
                .findByContactIdAndDefaultAccountTrue(contactId);
        boolean makeDefault = data.defaultAccount() || currentDefaults.isEmpty();
        if (data.defaultAccount()) {
            currentDefaults.forEach(ContactBankAccount::clearDefault);
            bankAccountRepository.flush();
        }
        // saveAndFlush - сабаби addAddress'даги изоҳда
        return bankAccountRepository.saveAndFlush(new ContactBankAccount(contact,
                data.bankName().strip(), Strings.blankToNull(data.bankCode()),
                accountNumber, currencyService.requireOrNull(data.currency()),
                makeDefault));
    }

    /** Банк реквизитини ўчиради (Payment боғлангач guard қўшилади - spec). */
    public void deleteBankAccount(UUID bankAccountId) {
        bankAccountRepository.delete(bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new NotFoundException("Банк реквизити топилмади: " + bankAccountId)));
    }

    // ---- валидация ва ёрдамчилар ----

    /** Умумий валидация: ном, валюта, term, email, ИНН, credit limit. */
    private void validate(ContactType type, ContactData data, UUID selfId) {
        if (data.displayName() == null || data.displayName().isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_CON_001, "Display name бўш бўлиши мумкин эмас");
        }
        repository.findByDisplayName(data.displayName().strip())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_CON_002, "Бу ном банд: " + data.displayName().strip());
                });
        // Валюта коди каталогга қарши шу ерда текширилади - apply()'да
        // requireOrNull ўша Currency entity'ни боғлайди
        currencyService.requireOrNull(data.currency());
        if (data.paymentTermId() != null && !paymentTermService.exists(data.paymentTermId())) {
            throw new BusinessRuleException(BusinessRule.BR_CON_003, "Тўлов шарти топилмади");
        }
        requireValidEmail(data.email());
        String taxId = Strings.blankToNull(data.taxId());
        if (taxId != null) {
            repository.findByTaxId(taxId)
                    .filter(other -> !other.getId().equals(selfId))
                    .ifPresent(other -> {
                        throw new BusinessRuleException(BusinessRule.BR_CON_005,
                                "Бу ИНН банд: " + taxId + " («" + other.getDisplayName() + "»)");
                    });
        }
        if (data.creditLimit() != null) {
            if (type != ContactType.CUSTOMER) {
                throw new BusinessRuleException(BusinessRule.BR_CON_006,
                        "Credit limit фақат харидор (customer) учун киритилади");
            }
            if (data.creditLimit().signum() < 0) {
                throw new BusinessRuleException(BusinessRule.BR_CON_006,
                        "Credit limit манфий бўлиши мумкин эмас: " + data.creditLimit());
            }
        }
        if (data.monthlySalary() != null) {
            if (type != ContactType.EMPLOYEE) {
                throw new BusinessRuleException(BusinessRule.BR_CON_011,
                        "Ойлик oklad фақат ходим (employee) учун киритилади");
            }
            if (data.monthlySalary().signum() < 0) {
                throw new BusinessRuleException(BusinessRule.BR_CON_011,
                        "Ойлик oklad манфий бўлиши мумкин эмас: " + data.monthlySalary());
            }
        }
    }

    /**
     * Контакт валютаси ҚУЛФИ (BR-CON-012, QBO қатъий, Arbitr-087):
     * контакт камида битта POSTED ҳужжатда қатнашган бўлса currency
     * ўзгартирилмайди - қиймат→қиймат ва қиймат→null доим тақиқ (QBO
     * хулқи: бошқа валюта керак бўлса янги контакт очилади). Ўтиш
     * қоидаси (мавжуд маълумот учун): null (home) → қийматга БИРИНЧИ
     * тўлдиришга рухсат, фақат янги қиймат POSTED ҳужжатларнинг ҲАММА
     * валюталарига тенг бўлса (docs/modules/multi-currency.md).
     * Валюта ўзгармаган save'да сўров ЮРМАЙДИ (ҳар таҳрирда 8-UNION
     * қиммат бўлмасин).
     */
    private void enforceCurrencyLock(Contact contact, String requestedCurrency) {
        String oldCode = contact.getCurrency() == null
                ? null : contact.getCurrency().getCode();
        String newCode = Strings.blankToNull(requestedCurrency);
        if (java.util.Objects.equals(oldCode, newCode)) {
            return;
        }
        List<String> postedCodes = postedDocumentCurrencies(contact.getId());
        if (postedCodes.isEmpty()) {
            return;
        }
        if (oldCode != null) {
            throw new BusinessRuleException(BusinessRule.BR_CON_012,
                    "«" + contact.getDisplayName() + "» валютаси ўзгартирилмайди: "
                    + "контакт POSTED ҳужжатларда қатнашган. Бошқа валюта учун "
                    + "янги контакт очилади (QBO услуби)");
        }
        if (postedCodes.size() > 1 || !postedCodes.get(0).equals(newCode)) {
            throw new BusinessRuleException(BusinessRule.BR_CON_012,
                    "«" + contact.getDisplayName() + "» валютасини " + newCode
                    + " қилиб бўлмайди: контактнинг POSTED ҳужжатлари валютаси "
                    + String.join(", ", postedCodes) + " - биринчи тўлдириш фақат "
                    + "ўша валютага мос бўлиши мумкин");
        }
    }

    /**
     * Контакт қатнашган POSTED ҳужжатларнинг DISTINCT валюта кодлари
     * (BR-CON-012 учун). AP/AR ҳужжат ва тўлов жадваллари қамралади;
     * Estimate/PO GL'сиз - POSTED мақоми йўқ, кирмайди. UUID id глобал
     * ноёб бўлгани учун битта сўров иккала контакт типини ҳам қоплайди
     * (customer жадвалларида vendor id бўш қайтади ва аксинча).
     */
    private List<String> postedDocumentCurrencies(UUID contactId) {
        // Native сўров Hibernate auto-flush'ни тетикламайди - шу
        // транзакцияда энди яратилган ҳужжатлар ҳам кўриниши учун аввал
        // flush (одатий оқимда ҳужжатлар аллақачон commit'ланган - арзон)
        repository.flush();
        return jdbc.sql("""
                SELECT DISTINCT cur.code FROM (
                    SELECT currency_id FROM invoice          WHERE customer_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM credit_memo      WHERE customer_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM sales_receipt    WHERE customer_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM refund_receipt   WHERE customer_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM invoice_payment  WHERE customer_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM bill             WHERE vendor_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM vendor_credit    WHERE vendor_id = :id AND status = 'POSTED'
                    UNION SELECT currency_id FROM bill_payment     WHERE vendor_id = :id AND status = 'POSTED'
                ) doc JOIN currency cur ON cur.id = doc.currency_id
                ORDER BY cur.code
                """)
                .param("id", contactId)
                .query(String.class)
                .list();
    }

    /** Email берилган бўлса форматини текширади (контакт ва шахс учун умумий). */
    private void requireValidEmail(String email) {
        if (email != null && !email.isBlank()
                && !EmailFormat.isValid(email.strip())) {
            throw new BusinessRuleException(BusinessRule.BR_CON_004, "Email формати нотўғри: " + email);
        }
    }

    /**
     * Форма маълумотларини entity'га кўчиради (тозалаб). Валюта КОДИ
     * алоҳида параметр - {@code data.currency()} эмас: чақирувчи уни
     * {@link #effectiveCurrencyCode} орқали coalesce қилган (бўш форма
     * қиймати home ёки мавжуд контакт валютасига айланган), шунда
     * контакт валютаси server'да ҳеч қачон null сақланмайди (Arbitr-159).
     */
    private void apply(Contact contact, ContactData data, String currencyCode) {
        contact.update(data.displayName().strip(),
                Strings.blankToNull(data.companyName()),
                Strings.blankToNull(data.firstName()),
                Strings.blankToNull(data.lastName()),
                Strings.blankToNull(data.email()),
                Strings.blankToNull(data.phone()),
                currencyService.requireOrNull(currencyCode),
                data.paymentTermId(),
                Strings.blankToNull(data.taxId()),
                data.creditLimit(),
                data.monthlySalary(),
                Strings.blankToNull(data.notes()));
    }

    /**
     * Контактга ёзиладиган эффектив валюта КОДИ (Arbitr-159, coalesce) -
     * контакт валютаси server'да ҳеч қачон null сақланмаслиги учун
     * (087 «client қийматига ишонилмайди» фалсафаси server кафолати
     * билан тўлдирилади: UI home'ни юбормаса ҳам - quick-add, API,
     * эски форма - null ёзилмайди).
     *
     * <p>НЕГА coalesce (наив «бўш → home» ХАТО): оддий blank→home
     * update'да эски ЧЕТ ВАЛЮТАЛИ контакт (EUR) валютаси бўш келганда
     * home'га РЕСЕТЛАНИБ кетарди - POSTED ҳужжат бўлмаса 087 қулфи
     * value→value ни тўсмайди (жимгина ўзгарарди), POSTED бўлса
     * BR-CON-012 портларди. Мавжудни устувор олиш ресетни олдини олади,
     * эски null (legacy) контактни эса кейинги сақлашда home кодга
     * кўчиради (миграция).
     *
     * @param requested форма юборган валюта коди (null/бўш бўлиши мумкин)
     * @param existing  мавжуд контакт (create оқимида null)
     * @return сақланадиган валюта коди - ҳеч қачон null/бўш эмас (форма
     *         қиймати, ёки мавжуд контакт валютаси, ёки home)
     */
    private String effectiveCurrencyCode(String requested, Contact existing) {
        String req = Strings.blankToNull(requested);
        if (req != null) {
            return req;
        }
        if (existing != null && existing.getCurrency() != null) {
            return existing.getCurrency().getCode();
        }
        return settingsService.homeCurrency();
    }


    /** Давлат коди каби қисқа кодларни тозалайди: бўш → null, upper-case. */
    private String upperOrNull(String value) {
        String v = Strings.blankToNull(value);
        return v == null ? null : v.toUpperCase();
    }
}
