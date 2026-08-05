# Ledger модули - SPEC

## Мақсад
Бухгалтерия ядроси: счётлар режаси, журнал проводкалари, ягона posting
нуқтаси. Бошқа барча модуллар шу модулга суянади.

Проводка эталони: docs/posting-rules.md - PostingService орқали
ёзиладиган ҳар проводка шу жадвалларга қатъий мос (темир қоида 8).
GL'дан ўқийдиган ҳисоботлар: docs/modules/reports.md. BR каталоги:
docs/business-rules.md (BR-LED/BR-COA). (Ҳаволалар: Ulugbek-030.)

## Entity'лар

### Account (account) - QBO Chart of Accounts услубида
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | PK (UUIDv7) |
| name | varchar(255), unique | Асосий идентификатор (QBO услуби) |
| classification | enum | ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE - detail type'дан келиб чиқади |
| type | enum AccountType | QBO Account Type (15 та: BANK, AR, FIXED_ASSET...) |
| detail_type | enum AccountDetailType | Фойдаланувчи танлайдиган ЯГОНА тур майдони (~75 та) |
| code | varchar(10) nullable | Ихтиёрий рақам; киритилса unique (partial index) |
| description | text nullable | QBO Description |
| parent_id | UUID nullable | sub-account иерархияси |
| postable | boolean | гуруҳ счётига проводка тақиқ |
| currency | varchar(3) nullable | валюта счёти бўлса (USD банк) |
| active | boolean | |

Тизим счётлари (AR, AP, Inventory, Undeposited Funds, Exchange
Gain/Loss...) кодга эмас, **detail type**'га қараб топилади:
`AccountRepository.findByDetailType` (service қатлами
`AccountService.findSystemAccount` - фақат фаол **postable** счёт;
гуруҳ ота ҳеч қачон танланмайди). CSV импорт формати:
`name;detailType;parentName;postable;currency;code`. Импорт ном
бўйича idempotent; код банд бўлса счёт кодсиз яратилади (warning),
импорт йиқилмайди.

### Default chart (coa/default-chart.csv) - дарахт ва кодлар

QBO default chart'и ясси ва рақамсиз - дарахт ва кодлар онгли кичик
кенгайтма (sub-account ва Account numbers механикаси QBO'ники,
фойдаланувчи қарори, Arbitr-126). Рақамлаш IFRS услуби:
1xxx актив, 2xxx мажбурият, 3xxx капитал, 4xxx даромад, 5xxx таннарх,
6xxx операцион харажат, 7xxx бошқа даромад/харажат. Жами 51 счёт:
42 postable + 9 гуруҳ ота (postable=false).

Гуруҳ отанинг detail type'и - болалар билан бир хил AccountType'даги
вакил тур (QBO қоидаси: sub-account ота билан бир AccountType'да).
Вакил тур ҳеч қачон «ягона» тизим тури эмас (AR/AP/INVENTORY каби) -
акс ҳолда импортнинг BR-COA-010 ҳимояси болани яратмай қўярди.
Истисно: 1040 Тушумлар транзити (UNDEPOSITED_FUNDS) расман
OTHER_CURRENT_ASSET, лекин тасдиқланган дарахтда пул гуруҳида (IFRS
«пул ва эквивалентлари» кўриниши; тизим ота-бола тур тенглигини
мажбурламайди, ҳисоботлар счётнинг ўз туридан юради).

| Код | Счёт | Detail type | Изоҳ |
|---|---|---|---|
| 1000 | Пул маблағлари | CASH_ON_HAND | гуруҳ |
| 1010 | - Банк ҳисобварағи | CHECKING | |
| 1020 | - Валюта ҳисобварағи (USD) | CHECKING | USD |
| 1030 | - Касса | CASH_ON_HAND | |
| 1040 | - Тушумлар транзити | UNDEPOSITED_FUNDS | |
| 1100 | Дебиторлик (AR) | ACCOUNTS_RECEIVABLE | |
| 1200 | Заҳиралар | OTHER_CURRENT_ASSETS | гуруҳ |
| 1210 | - Товар-моддий заҳиралар | INVENTORY | |
| 1220 | - Landed cost клиринг | INVENTORY_CLEARING | |
| 1300 | Олдиндан тўланган харажатлар | PREPAID_EXPENSES | |
| 1500 | Асосий воситалар | OTHER_FIXED_ASSETS | гуруҳ |
| 1510 | - Машина ва ускуналар | MACHINERY_AND_EQUIPMENT | |
| 1520 | - Жамғарилган амортизация | ACCUMULATED_DEPRECIATION | |
| 2000 | Кредиторлик (AP) | ACCOUNTS_PAYABLE | |
| 2100 | Солиқ мажбуриятлари | OTHER_CURRENT_LIABILITIES | гуруҳ |
| 2110 | - ҚҚС тўланадиган | SALES_TAX_PAYABLE | |
| 2120 | - Даромад солиғи тўланадиган | INCOME_TAX_PAYABLE | |
| 2200 | Иш ҳақи мажбуриятлари | OTHER_CURRENT_LIABILITIES | гуруҳ |
| 2210 | - Иш ҳақи бўйича мажбурият | PAYROLL_CLEARING | |
| 2220 | - Иш ҳақи солиқлари мажбурияти | PAYROLL_TAX_PAYABLE | |
| 2400 | Олинган аванслар | OTHER_CURRENT_LIABILITIES | |
| 3000 | Устав капитали | COMMON_STOCK | |
| 3100 | Тақсимланмаган фойда | RETAINED_EARNINGS | |
| 3900 | Бошланғич баланс капитали | OPENING_BALANCE_EQUITY | |
| 4000 | Товар сотув даромади | SALES_OF_PRODUCT_INCOME | |
| 4100 | Хизмат даромади | SERVICE_FEE_INCOME | |
| 4900 | Чегирма ва қайтаришлар | DISCOUNTS_REFUNDS_GIVEN | |
| 5000 | Товар таннархи (COGS) | SUPPLIES_MATERIALS_COGS | |
| 5100 | Ташиш ва етказиб бериш (таннарх) | SHIPPING_FREIGHT_DELIVERY_COS | |
| 5200 | Inventory камомад/ортиқча | OTHER_COSTS_OF_SERVICE_COS | |
| 6100 | Иш ҳақи харажатлари | PAYROLL_EXPENSES | гуруҳ |
| 6110 | - Иш ҳақи харажати | PAYROLL_EXPENSES | |
| 6120 | - Иш ҳақи солиқ харажати | PAYROLL_EXPENSES | |
| 6200 | Бино ва хўжалик | RENT_OR_LEASE_OF_BUILDINGS | гуруҳ |
| 6210 | - Ижара | RENT_OR_LEASE_OF_BUILDINGS | |
| 6220 | - Коммунал хизматлар | UTILITIES | |
| 6230 | - Таъмирлаш | REPAIR_MAINTENANCE | |
| 6300 | Офис ва материаллар | OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES | гуруҳ |
| 6310 | - Офис харажатлари | OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES | |
| 6320 | - Сарф материаллари | SUPPLIES_MATERIALS | |
| 6400 | Ташқи хизматлар | LEGAL_PROFESSIONAL_FEES | гуруҳ |
| 6410 | - Профессионал хизматлар | LEGAL_PROFESSIONAL_FEES | |
| 6420 | - Банк хизматлари | BANK_CHARGES | |
| 6500 | Реклама ва маркетинг | ADVERTISING_PROMOTIONAL | |
| 6600 | Хизмат сафари | TRAVEL | |
| 6700 | Солиқлар | TAXES_PAID | |
| 6800 | Амортизация | DEPRECIATION | |
| 6900 | Бошқа операцион харажатлар | OTHER_MISCELLANEOUS_SERVICE_COST | |
| 7000 | Фоиз даромади | INTEREST_EARNED | |
| 7100 | Валюта курси фарқи | EXCHANGE_GAIN_OR_LOSS | |
| 7500 | Фоиз харажати | INTEREST_PAID | |

Мавжуд базаларга таъсир йўқ: импорт ном бўйича idempotent, кодлар ва
дарахт фақат янги ўрнатиш ёки factory reset'да келади. Жонли/dev
базага бир мартали миграция - алоҳида қарор (Arbitr-126 кўламидан
ташқари).

**Account ўчирилмайди** - фақат `active=false` (QBO make inactive).
Сабаб: тарихий проводка сатрлари account id'га боғланган; FK'лар
атайлаб ON DELETE'сиз (NO ACTION). Ўчириш тугмаси ҳеч қачон
қўшилмайди - бу онгли дизайн қарори.

### JournalEntry (journal_entry)
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | |
| entry_number | varchar(20), unique | JE-2026-000001, sequence'дан |
| entry_date | date | |
| description | text | |
| status | enum | DRAFT, POSTED, REVERSED |
| source_module | varchar(30) nullable | MANUAL, SALES, PURCHASE... |
| source_document_id | UUID nullable | |
| reversed_by_id | UUID nullable | сторно entry'га ссилка |
| posted_at | timestamptz nullable | |

### JournalEntryLine (journal_entry_line)
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | |
| entry_id | UUID FK | |
| line_no | int | |
| account_id | UUID FK | postable бўлиши шарт |
| debit / credit | Money embedded | биттаси нол |
| contact_id | UUID nullable | dimension |
| warehouse_id | UUID nullable | dimension |
| item_id | UUID nullable | dimension |
| memo | varchar(500) | |

## PostingService - ягона public API
```java
public interface PostingService {
 /** DRAFT entry яратади (валидациясиз сақлаш мумкин эмас -
 структура валидацияси доим ишлайди) */
 JournalEntry createDraft(JournalEntryRequest request);
 /** Валидация + POSTED. Инвариантлар бузилса PostingException */
 JournalEntry post(UUID entryId);
 /** createDraft + post битта транзакцияда - модуллар учун асосий йўл */
 JournalEntry createAndPost(JournalEntryRequest request);
 /** Сторно: тескари entry яратиб post қилади, асл entry → REVERSED */
 JournalEntry reverse(UUID entryId, LocalDate reversalDate, String reason);
}
```

## Валидация (post пайтида, тартиб билан)
1. Камида 2 line.
2. Ҳар line: debit XOR credit (баҳоси > 0).
3. Ҳар line: account мавжуд, active, postable.
4. sum(debitBase) == sum(creditBase) - home валютада, 4 хона аниқликда.
5. Ҳар Money: baseAmount == amount * exchangeRate (0.0001 tolerance).
6. status == DRAFT бўлгандагина post мумкин.

createDraft пайтида структура валидацияси (1, 2, 3, 5) доим ишлайди;
баланс (4) фақат post'да текширилади - QuickBooks услубида draft
мувозанатсиз сақланиши мумкин.

## Home currency ва timezone
PostingService baseAmount'ларни CompanySettings'даги home currency
контекстида қабул қилади. Биринчи POSTED entry мавжуд бўлса
CompanySettingsService home currency ўзгартиришни рад этади
(HomeCurrencyLock порти орқали). Home currency Currency каталогида
мавжуд ва фаол бўлиши шарт. Timezone (IANA id) ҳам CompanySettings'да -
вақтлар базада UTC, экранда шу минтақада (Fmt.dt).

## JournalEntryRequest
Сатрлар счётни **id орқали** кўрсатади (Line.accountId) - код
ихтиёрий бўлгани учун кодга таяниб бўлмайди. Модуллар тизим
счётларини detail type орқали топади, кейин id билан проводка қилади.

## Счёт амаллари (register, T1)
AccountTransactionsService.register(accountId, from, to) - public ўқиш
методи (ui-navigation-display.md T1): счёт қатнашган POSTED/REVERSED
проводка сатрлари сана тартибида (тартиб: entry_date → posted_at →
entry id → line_no), давр бошидаги очилиш қолдиғи ва ҳар сатрдан
кейинги жорий қолдиқ (home валютада, signed - мусбат дебет) билан.
Сатрда contactId dimension қайтади; контрагент НОМИни ечиш ledger'да
эмас - ledger contact модулига боғлана олмайди, ном экран қатламида
ҳал қилинади.

## Тестлар (мажбурий)
- post: баланс тўғри → POSTED
- post: debit != credit → PostingException
- post: postable=false счёт → PostingException
- reverse: тескари суммалар, асл entry REVERSED
- POSTED entry'ни ўзгартириш → exception
- multi-currency line: USD line home валютада балансланади

Тестлар локал PostgreSQL'даги averpo_test базасида ишлайди
(src/test/resources/application-test.yml, Liquibase drop-first билан
контекст кўтарилишида schema қуради, тестлар @Transactional rollback).

## Экранлар (JTE)
- /accounts - иерархик рўйхат (дарахт), CRUD
- /journal-entries - рўйхат (filter: сана, статус, модул)
- /journal-entries/new - қўлда проводка формаси (HTMX билан line қўшиш)
- /reports/trial-balance - давр танлаш, счёт бўйича Dt/Cr айланма + қолдиқ
- /accounts/{id}/transactions - счёт амаллари (register): давр филтри,
 жорий қолдиқ, ҳар қатор ўз JE'сига боради (T1)
