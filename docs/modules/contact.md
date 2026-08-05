# Contact модули - SPEC (3-босқич)

Эталон: QBO Customers ва Vendors рўйхатлари.

## Мақсад
Харидорлар (Customer) ва мол етказиб берувчилар (Vendor) каталоги.
Sales (7-босқич) AR'ни, Purchases (6-босқич) AP'ни контакт кесимида
юритади - JournalEntryLine.contactId dimension шу ерга ишора қилади.

## Дизайн қарори: битта жадвал, иккита рўйхат
QBO'да Customers ва Vendors алоҳида рўйхатлар, лекин номлар ЯГОНА
namespace'да (бир хил display name билан ҳам customer ҳам vendor
бўлолмайди). Биз ҳам шундай: битта `contact` жадвали + `type` устуни,
display name глобал unique. Экранлар алоҳида: /customers ва /vendors.

## Entity

### Contact (contact)
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | PK (UUIDv7) |
| type | enum ContactType | CUSTOMER, VENDOR, EMPLOYEE |
| display_name | varchar(255), unique | Ягона идентификатор (QBO услуби) |
| company_name | varchar(255) nullable | Юридик ном |
| first_name / last_name | varchar(100) nullable | Масъул шахс |
| email | varchar(255) nullable | |
| phone | varchar(50) nullable | |
| currency | varchar(3) nullable | Контакт валютаси (QBO multicurrency услуби). Бўш - home currency. Қулф: POSTED ҳужжат бор бўлса ўзгартирилмайди - BR-CON-012 (DEC-087, multi-currency.md) |
| payment_term_id | UUID nullable | PaymentTerm каталогига FK (QBO Terms услуби) |
| tax_id | varchar(20) nullable | ИНН - partial unique WHERE tax_id IS NOT NULL (BR-CON-005); формат текшируви йўқ (хорижий контрагентлар эркин) |
| credit_limit | numeric(19,4) nullable | Фақат CUSTOMER, мижоз валютасида, манфий эмас (BR-CON-006) |
| notes | text nullable | Эркин изоҳ |
| active | boolean | QBO «make inactive» - ўчириш йўқ, тарих сақланади |

Эслатма: дастлабки billing_address/shipping_address оддий
матн устунлари contact_address жадвалига кўчирилиб олиб ташланган
(changeset 016).

ContactType enum: CUSTOMER («Харидор»), VENDOR («Етказиб берувчи»),
EMPLOYEE («Ходим» - Payroll Lite, атайлаб фарқ №2; ойлик oklad
monthly_salary майдони BR-CON-011, тафсилот payroll.md) - титуллар
i18n bundle'да (contact.type.*).

### PaymentTerm (payment_term, shared модулда)
QBO'даги Terms рўйхати - Invoice/Bill ҳам кейин тўғридан-тўғри
ишлатади, шунинг учун shared'да (Currency паттерни).

| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | PK (UUIDv7) |
| name | varchar(100), unique | «Due on receipt», «Net 30» |
| days | int | Ҳужжат санасидан неча кунда тўланади (0 - дарҳол) |
| active | boolean | |

Seed: Due on receipt (0), Net 15, Net 30, Net 60.

## Service - public API
```java
public class ContactService {
    Contact create(ContactType type, /* форма майдонлари */ ...);
    Contact update(UUID id, ...);
    Contact get(UUID id);
    List<Contact> byType(ContactType type, boolean includeInactive);
    // Кейинги босқичлар: sales/purchase модуллари контакт номи ва
    // currency'си учун фақат шу public service орқали мурожаат қилади
}
```

## Валидация
1. display_name бўш эмас, глобал unique (типидан қатъи назар - QBO услуби).
2. currency берилса Currency каталогида мавжуд ва фаол бўлиши шарт.
3. payment_term_days берилса >= 0.
4. email берилса оддий формат текшируви.

## Экранлар (JTE, i18n, QBO услуби)
- /customers - рўйхат: Display name, Компания, Email, Телефон, Валюта,
  Ҳолат. «Nofaollarni кўрсатиш» тогл. Қатор → таҳрир.
- /vendors - худди шу, type=VENDOR.
- Форма - full-screen layout/form.jte (QBO паттерни), sticky footer.
- Ўчириш ЙЎҚ - фақат active тогл (QBO make inactive).

## Тестлар (мажбурий)
- create: displayName unique - дубликат IllegalArgumentException
  (customer ва vendor ўртасида ҳам).
- currency каталогда йўқ → хато; фаол эмас → хато.
- update: ўз номини сақлаш OK, бошқаники банд.
- byType фақат сўралган типни қайтаради; inactive филтри ишлайди.

## Кенгайтма: манзил, шахс, банк реквизит (old-erp-ideas §3)

Учта child жадвал - барчаси contact модули ичида, ташқарига фақат
ContactService орқали очилади. Child қаторлар hard delete қилинади
(ҳужжатлар ҳали боғланмаган; Payment реквизитга боғлангач guard
қўшилади). Таҳрирлаш MVP'да йўқ - ўчириб қайта қўшилади.

### ContactAddress (contact_address)
| Майдон | Тип | Изоҳ |
|---|---|---|
| contact_id | UUID FK | |
| address_type | enum | BILLING, SHIPPING, LEGAL |
| address_line1 | varchar(500), шарт | BR-CON-007 |
| address_line2 | varchar(200) | |
| city / region | varchar(100) | |
| postal_code | varchar(20) | |
| country_code | varchar(2) | ISO 3166-1 alpha-2, upper-case |
| is_default | boolean | Ҳар (contact, type)да биттагина - partial unique |

Default мантиғи (QBO услуби, хато эмас): турдаги биринчи манзил
автоматик default; кейинги манзил default деб белгиланса эскиси
автоматик бўшатилади.

### ContactPerson (contact_person)
| Майдон | Тип | Изоҳ |
|---|---|---|
| full_name | varchar(200), шарт | BR-CON-008 |
| position | varchar(100) | Лавозим |
| phone / email | varchar | email формати BR-CON-004 билан текширилади |
| is_primary | boolean | Контактда биттагина - default мантиғи манзилдагидек |

### ContactBankAccount (contact_bank_account)
| Майдон | Тип | Изоҳ |
|---|---|---|
| bank_name | varchar(200), шарт | BR-CON-009 |
| bank_code | varchar(20) | МФО |
| account_number | varchar(50), шарт | UNIQUE(contact, account_number) - BR-CON-010 |
| currency_id | UUID FK nullable | Currency каталоги (темир қоида №11) |
| is_default | boolean | Контактда биттагина - default мантиғи манзилдагидек |

Эски лойиҳадаги status enum олинмади (соддалаштириш).

### Service API кенгайтмаси
```java
List<ContactAddress> addresses(UUID contactId);
ContactAddress addAddress(UUID contactId, AddressData data);
void deleteAddress(UUID addressId);
// persons / bankAccounts учун худди шу учлик
```

### Тестлар (кенгайтма)
- ИНН дубликат (customer↔vendor орасида ҳам) - BR-CON-005; NULL ИНН
  кўп контактда эркин; ўз ИННини сақлаш OK.
- Credit limit vendor'га ёки манфий - BR-CON-006.
- Биринчи манзил авто-default; янги default эскисини бўшатади;
  line1 бўш - BR-CON-007.
- Шахс: исм бўш - BR-CON-008; primary алмашуви.
- Банк: мажбурий майдонлар - BR-CON-009; шу контактда рақам дубликати -
  BR-CON-010; бошқа контактда шу рақам OK.
- EMPLOYEE oklad (monthly_salary) фақат EMPLOYEE учун, манфий эмас -
  BR-CON-011 (payroll.md).
- Контакт валютаси қулфи - BR-CON-012 (DEC-087; тафсилот
  multi-currency.md «Контакт валютаси»).

## Кейинги босқичларга мослик
- 6/7-босқич: Invoice/Bill контактга боғланади, contact.currency
  ҳужжат валютасини default қилади, биринчи POSTED ҳужжатдан кейин
  валюта қулфланади (CompanySettings home currency қулфи паттерни).
- payment_term_days'дан due date ва AR/AP aging ҳисобланади.
