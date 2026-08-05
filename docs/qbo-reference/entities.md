# QBO Online маълумот модели - расмий маълумотнома

Манба: Intuit расмий Java SDK'сидаги XSD схемалар (шу папкада сақланган):
- `Finance.xsd` - барча транзакция ва каталог entity'лари
- `IntuitNamesTypes.xsd` - Customer/Vendor базаси (NameBase)

Олинган жойи: `github.com/intuit/QuickBooks-V3-Java-SDK`
(`ipp-v3-java-data/src/main/xsd/`), Apache 2.0, юклаб олинган сана:
2026-07-06. Расмий SDK модули: `ipp-v3-java-data`.
Бу айнан developer.intuit.com веб-ҳужжатлари generate
қилинадиган манба - веб-саҳифага мурожаат шарт эмас, schema саволига
жавоб шу файллардан олинади.

**Ўқиш қоидаси**: XSD'да ҳар майдон изоҳида `Product:` белгиси бор.
Бизга фақат `QBO` ва `ALL` тегишли; `QBW` (Desktop) майдонлари эталонга
КИРМАЙДИ. Мамлакатга хос майдонлар (FR JournalCode, IN GSTIN, CA T4A,
UK CIS...) ҳам четда қолади.

**Эталон қоидаси**: Averpo QBO Online'дан ташқарига чиқмайди.
Атайлаб фарқ ИККИТА (engineering-rules.md билан мос, 2026-07-08):
(1) **multi-warehouse inventory** (QBO'да омбор тушунчаси йўқ);
landed cost ва Unit каталоги шу кенгайтманинг таркибий қисмлари;
(2) **Payroll Lite** (QBO ядросида payroll йўқ - алоҳида пулли
маҳсулот; docs/modules/payroll.md). Қолган ҳамма жойда икки ечим
орасида иккиланилса - шу ҳужжат ва XSD текширилади.

---

## 1. Account (счёт)

| QBO майдони | Тип | Бизда | Изоҳ |
|---|---|---|---|
| Name | string, Max=100, Required | `name` unique | Мос. Бизда узунлик чексиз - 100 чегара қўйиш мумкин |
| SubAccount + ParentRef | boolean + ref | `parent` (ManyToOne) | Мос - SubAccount флаги parent'дан келиб чиқади |
| Description | string, Max=100 | `description` text | Мос |
| FullyQualifiedName | ReadOnly, `Parent:Sub` кўриниши | йўқ (экранда дарахт) | Ҳисобланадиган майдон, сақлаш шарт эмас |
| Active | boolean | `active` | Мос. QBO: «ўчириш йўқ, фақат inactive» - бизда ҳам |
| Classification | enum (5) | `classification` | Мос - иккисида ҳам derived |
| AccountType | enum (16) | `type` (15) | 16-чиси `Non-Posting` (QBO'да estimate/PO учун) - бизга керак эмас, онгли қисқартма |
| AccountSubType | string (QBO detail type) | `detailType` (86) | Мос - қуйида §1.2; жуфти чала турлар Arbitr-016 да тўлдирилди (BAD_DEBTS, ACCUMULATED_AMORTIZATION, INTANGIBLE_ASSETS, ACCUMULATED_AMORTIZATION_OF_OTHER_ASSETS, UNAPPLIED_CASH_BILL_PAYMENT_EXPENSE) |
| AcctNum | string | `code` (10) partial unique | Мос. QBO'да unique мажбурланмайди, бизда қатъийроқ - зарарсиз |
| AcctNumExtn | string (QBO) | йўқ | AR/AP счётига қўшимча рақам - керак бўлса кейин |
| OpeningBalance / OpeningBalanceDate | decimal + date | алоҳида Opening Balance оқими | Мос - бизда PostingService орқали, натижа бир хил |
| CurrentBalance / CurrentBalanceWithSubAccounts | decimal | сақланмайди, ledger'дан ҳисобланади | Онгли фарқ ЭМАС, яхшиланиш: QBO API'да ҳам бу ReadOnly ҳисобланган қиймат |
| CurrencyRef | ref | `currency` (ManyToOne) | Мос |
| TaxAccount / TaxCodeRef | boolean / ref (QBW) | йўқ - контрол счёт detail type орқали | Tax қурилган (2026-07-07, changeset 032, tax.md): ҚҚС контрол счёти SALES_TAX_PAYABLE detail type билан топилади - Account'да алоҳида солиқ майдони АТАЙЛАБ йўқ (булар QBW мероси майдонлар) |
| OnlineBankingEnabled / FIName | ReadOnly | йўқ | Banking босқичида кўрилади |

Бизда бор, QBO Account'да йўқ: `postable` (гуруҳ счётига проводка
тақиқ). QBO'да parent счёт ҳам postable. Бу тизимли оғиш - ё олиб
ташланади, ё architecture.md'да онгли қарор сифатида ҳужжатланади.

### 1.1 AccountTypeEnum (расмий 16 та)

Bank, Accounts Receivable, Other Current Asset, Fixed Asset,
Other Asset, Accounts Payable, Credit Card, Other Current Liability,
Long Term Liability, Equity, Income, Cost of Goods Sold, Expense,
Other Income, Other Expense, **Non-Posting**.

Бизда 15 та - `Non-Posting` йўқ (estimate/purchase order каби
проводкасиз ҳужжатлар учун; бизда бундай ҳужжатлар GL'га умуман
бормайди, тур ҳам керак эмас).

### 1.2 AccountSubTypeEnum - номлашдаги фарқлар (✅ БАЖАРИЛДИ, 2026-07-06)

XSD'да 282 қиймат бор (US + FR/UK/IN/CA global вариантлари билан).
4.5-босқичда (detail-type-rename-plan.md, changeset 018) қуйидаги
мосликлар амалга оширилди - энди enum расмий номларнинг SNAKE_CASE
кўриниши билан 1:1 мос:

| Эски ном | Янги ном (QBO расмий) |
|---|---|
| SUPPLIES_AND_MATERIALS_COGS | SUPPLIES_MATERIALS_COGS (`SuppliesMaterialsCogs`) |
| SUPPLIES | SUPPLIES_MATERIALS (`SuppliesMaterials`) |
| OFFICE_EXPENSES | OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES (`OfficeGeneralAdministrativeExpenses`) |
| PAID_IN_CAPITAL | PAID_IN_CAPITAL_OR_SURPLUS (`PaidInCapitalOrSurplus`) |
| TRUST_ACCOUNT | TRUST_ACCOUNTS (`TrustAccounts`) |
| PAYROLL_LIABILITIES | PAYROLL_CLEARING (`PayrollClearing`); қўшимча PAYROLL_TAX_PAYABLE (`PayrollTaxPayable`) янги тур сифатида киритилди |
| UNEARNED_REVENUE | олиб ташланди - қаторлар OTHER_CURRENT_LIABILITIES турига ўтди (расмийда йўқ; яқини global `AccrualsAndDeferredIncome`) |

`INVENTORY_CLEARING` - бизнинг landed cost кенгайтмамиз, QBO'да йўқ
(ҳужжатланган, тегилмаган). Янги detail type қўшилганда ФАҚАТ расмий
CamelCase номнинг SNAKE_CASE кўриниши олинади.

---

## 2. JournalEntry

QBO'да JE `Transaction` базасидан мерос олади. Base'даги муҳим
майдонлар (Product: QBO):

| QBO майдони | Жойи | Бизда | Изоҳ |
|---|---|---|---|
| DocNumber | header | `entryNumber` (sequence) | Мос - QBO'да `AutoJournalEntryNumber` preference'и ҳам бор |
| TxnDate | header | `entryDate` | Мос |
| PrivateNote | header | `description` | Мос |
| **CurrencyRef** | **header** | бизда Line даражасида (`Money.currency`) | **Тизимли фарқ**: QBO'да бутун JE битта валютада, курс ҳам header'да |
| **ExchangeRate** | **header** | бизда Line даражасида (`Money.exchangeRate`) | Юқоридаги билан бирга |
| Line[] | header | `lines` | Мос |
| Adjustment | JE | йўқ | Керак эмас (MVP) |
| HomeCurrencyAdjustment / EnteredInHomeCurrency | JE | йўқ | Unrealized қайта баҳолаш босқичида керак бўлади - schema'га ҳозирдан киритилмайди |
| TotalAmt / HomeTotalAmt | JE | сақланмайди | QBO'да ҳам ReadOnly ҳисобланган қиймат |

### JournalEntryLineDetail (Product: QBO майдонлари)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| PostingType (Debit/Credit) | `debit`/`credit` Money жуфти | Мос (XOR инварианти билан) |
| AccountRef | `account` | Мос |
| Entity (Customer/Vendor/Employee ref) | `contactId` dimension | Мос - Employee ҲАМ бор (2026-07-08, Payroll Lite: ContactType.EMPLOYEE, payroll ходим кесими GL contact'дан - AR/AP субледжер услуби) |
| DepartmentRef / ClassRef | ClassRef - бор (`class_id` сатрда, TxnClass каталоги); DepartmentRef (Location) - СПЕЦ ёзилди 2026-07-12 | Class tracking бор (2026-07-08, docs/modules/class-tracking.md): сатр даражасида, OFF/PER_TXN/PER_LINE, P&L by Class. Location (DepartmentRef) - docs/modules/location-tracking.md (2026-07-12, фойдаланувчи талаби): header даражали, OFF/ON, ёрлиқ қайта номланади, P&L by Location - ТАСДИҚ КУТМОҚДА |
| TaxCodeRef / TaxApplicableOn / TaxAmount | ҳужжат сатрларида (tax_rate_id + ставка snapshot) | Tax қурилган (2026-07-07, changeset 032, tax.md): проводка SALES_TAX_PAYABLE контрол счётига ҳужжат posting'ида тушади. Қолдиқ фарқ (аниқ): ҚЎЛДА JE'га алоҳида солиқ майдони киритилмаган - зарурат чиқса алоҳида карта |
| BillableStatus | йўқ | Billable expense - кейинги босқичлар |

**Қарор (2026-07-06 текширувидан)**: ҳужжат модуллари (Invoice, Bill,
Payment - 6/7-босқич) QBO услубида **битта ҳужжат = битта валюта**
қоидаси билан қурилади (валюта ва курс ҳужжат header'ида). Line
даражасидаги Money эркинлиги фақат қўлда JE учун ички имконият бўлиб
қолади ва ҳужжат оқимларига «оқиб» чиқмайди.

---

## 3. Item (Product/Service)

QBO ItemTypeEnum: Inventory, NonInventory, Service, Category, Bundle,
Group, Assembly, Fixed Asset, ... (кўпи Desktop). QBO Online'да амалда:
Inventory, NonInventory, Service, Bundle, Category.

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| Name (unique) | `name` unique | Мос |
| Sku | `sku` partial unique | Мос (QBO unique мажбурламайди - бизда қатъийроқ, зарарсиз) |
| Type | `type` (3) | Мос; Bundle кейинги босқич (roadmap'да бор) |
| Active | `active` | Мос - ўчириш йўқ |
| SubItem/ParentRef, ItemCategoryType | алоҳида `ItemCategory` entity | QBO'да категория ҳам Item (Type=Category); биздаги алоҳида entity - ҳужжатланган соддалаштириш (item.md §5) |
| Taxable / SalesTaxCodeRef / PurchaseTaxCodeRef | `salesTaxRateId` / `purchaseTaxRateId` (default ставкалар - сатр prefill) | Tax қурилган (2026-07-07, changeset 032, tax.md). Алоҳида Taxable флаги йўқ - ставкаси null item ўзи солиқсиз (соддалаштириш, маъно бир хил) |
| UnitPrice | `salesPrice` | Мос |
| Description | `salesDescription` | Мос |
| PurchaseDesc | `purchaseDescription` | Мос |
| PurchaseCost | `purchaseCost` | Мос |
| IncomeAccountRef | `incomeAccountId` | Мос (UUID, JPA эмас - модул қоидаси) |
| ExpenseAccountRef / COGSAccountRef | `expenseAccountId` | Мос |
| AssetAccountRef | `inventoryAssetAccountId` | Мос |
| TrackQtyOnHand / QtyOnHand / InvStartDate | Inventory модули (StockBalance) | Онгли: item формасида эмас, омбор кирим ҳужжати билан (item.md §4) |
| AvgCost | StockBalance.avgCost (item, warehouse) | Multi-warehouse кенгайтмаси: QBO'да компания бўйича битта, бизда омбор кесимида |
| ReorderPoint | `reorderPoint` | Мос |
| PrefVendorRef | йўқ | Кейинги босқичлар |
| UOMSetRef | `unit` (Unit каталоги) | QBW майдони; QBO Online'да UoM ЙЎҚ - биздаги Unit multi-warehouse кенгайтмасининг қисми (item.md §3 ҳужжатланган) |

---

## 4. Customer / Vendor

Иккиси ҳам `NameBase`'дан мерос олади. NameBase (Product: QBO
тегишлилари): Title, GivenName, MiddleName, FamilyName, Suffix,
CompanyName, **DisplayName** (unique, Customer↔Vendor↔Employee
namespace умумий), PrintOnCheckName, Active, PrimaryPhone, Mobile,
Fax, AlternatePhone, PrimaryEmailAddr, WebAddr.

### Customer (Product: QBO тегишлилари)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DisplayName unique | `displayName` глобал unique | Мос (битта жадвал + type - ҳужжатланган ечим, натижа бир хил) |
| GivenName/FamilyName | `firstName`/`lastName` | Мос |
| CompanyName | `companyName` | Мос |
| PrimaryEmailAddr / PrimaryPhone | `email` / `phone` | Мос |
| BillAddr / ShipAddr | `ContactAddress` (BILLING/SHIPPING/LEGAL) | QBO'да иккита embedded манзил; бизда рўйхат + LEGAL типи - Ўзбекистон реквизит талаби, кичик кенгайтма |
| Notes | `notes` | Мос |
| SalesTermRef | `paymentTermId` | Мос |
| **CreditLimit** | `creditLimit` | **Мос - QBO'да расман бор** (аввалги аудитда «QBO'да йўқ» дейилган эди - НОТЎҒРИ, XSD тасдиқлади) |
| CurrencyRef | `currency` | Мос (биринчи ҳужжатдан кейин қулф - QBO ҳам шундай) |
| PrimaryTaxIdentifier | `taxId` (ИНН) | Мос |
| Balance / OverDueBalance | сақланмайди - ledger'дан | QBO'да ҳам ҳисобланган қиймат |
| Job/ParentRef (sub-customer) | йўқ | Кейинги босқичлар - зарурати чиқса |
| Taxable / DefaultTaxCodeRef | йўқ | Tax қурилган (2026-07-07, changeset 032, tax.md), лекин КОНТАКТ даражасида default ставка йўқ - prefill item default'идан (сатрда). Қолдиқ фарқ (аниқ): зарурат чиқса алоҳида карта |
| PreferredDeliveryMethod | йўқ | Кейин |
| PaymentMethodRef | БОР (2026-07-07, Arbitr-033): payment_method каталоги (name+active; Type атайлаб йўқ - credit card кўлами РАД) + bank_transaction.payment_method_id; Expense экранида танланади | Мос (Type'сиз) |

### Vendor (Product: QBO тегишлилари)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DisplayName / CompanyName / контакт майдонлари | Contact (type=VENDOR) | Мос |
| TermRef | `paymentTermId` | Мос |
| CurrencyRef | `currency` | Мос |
| TaxIdentifier / BusinessNumber | `taxId` | Мос |
| **CreditLimit** | бизда фақат CUSTOMER'га (BR-CON-006) | QBO'да vendor'да ҳам бор - хоҳласак очиш мумкин, шошилинч эмас |
| **VendorPaymentBankDetail** | `ContactBankAccount` | **Мос - QBO'да vendor банк реквизити расман бор** (аввалги аудит тузатилди). Бизда customer'га ҳам очиқ - кичик кенгайтма |
| APAccountRef | йўқ - AP detail type орқали | Мос ечим (posting-rules) |
| Vendor1099 / T4A / CIS / GSTIN | йўқ | US/CA/UK/IN'га хос - бизга тегишли эмас |

`ContactPerson` (масъул шахслар рўйхати) - QBO'да фақат битта
GivenName/FamilyName бор; рўйхат бизнинг кичик кенгайтмамиз.

---

## 5. Term (тўлов шарти)

QBO Term: Name, Active, Type (STANDARD / DATE_DRIVEN), DueDays,
DiscountPercent, DiscountDays, DayOfMonthDue, DueNextMonthDays,
DiscountDayOfMonth.

Бизда: `name`, `days`, `active` - фақат STANDARD туримиз бор,
чегирма ва ой-кунига боғланган турлар йўқ. MVP соддалаштириши;
эрта тўлов чегирмаси керак бўлганда QBO схемаси бўйича кенгайтирилади
(Type + Discount майдонлари).

---

## 6. ExchangeRate

QBO (Product: QBO): SourceCurrencyCode, TargetCurrencyCode (доим home),
Rate, AsOfDate. Битта (source, date) га битта ёзув, семантика:
1 source = Rate home.

Бизда: `currency` (ManyToOne), `rateDate`, `rate`, `source`
(CBU/MANUAL). QBO = бир (source, date)га битта ёзув (upsert); бизда
**append-only тарих** - бир (currency, rate_date)га кўп ёзув, устига
ёзилмайди (Arbitr-022, changeset 033: uq_exchange_rate олиб ташланди,
source қўшилди). Бу ОНГЛИ фарқ (QBO'да курс тарихи йўқ, бизда аудит
изи учун сақланади; target доим home бўлгани учун устун сақланмайди).
`rateFor` семантикаси (санага тенг ёки олдинги энг охирги ёзув) QBO
хатти-ҳаракати билан ЎЗГАРИШСИЗ бир хил - амалдаги курс мос, фақат
storage тарихни сақлайди.

---

## 7. Preferences (CompanySettings'га мослик)

QBO CurrencyPrefs: MultiCurrencyEnabled, HomeCurrency.
QBO CompanyAccountingPrefs (Product: QBO тегишлилари):

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| HomeCurrency | `homeCurrency` | Мос + биринчи POSTED'дан кейин қулф (QBO ҳам шундай) |
| MultiCurrencyEnabled | доим ёқиқ | Соддалаштириш - зарарсиз |
| **BookCloseDate** | `closingDate` | Мос (BR-LED-020) |
| FirstMonthOfFiscalYear | `fiscalYearStartMonth` | Бор ✅ (9-босқичда қўшилган: changeset 024, BR-SET-004, /settings формасида) |
| UseAccountNumbers | доим ихтиёрий code | Мос (QBO default'и ҳам ўчиқ) |
| DefaultARAccount / DefaultAPAccount | detail type орқали топилади | Мос ечим |
| AutoJournalEntryNumber | доим авто (sequence) | Мос |
| TrackDepartments / ClassTracking* | ClassTracking* - бор (`track_classes`: OFF/PER_TXN/PER_LINE) | Class tracking бор (2026-07-08, docs/modules/class-tracking.md); TrackDepartments (Location) - спец ёзилди 2026-07-12 (docs/modules/location-tracking.md), ТАСДИҚ КУТМОҚДА. Warehouse dimension Class ўрнини босмайди |
| Timezone | `timezone` | QBO Preferences'да алоҳида йўқ (CompanyInfo'да) - бизники тўғри |
| InventoryValuation (AVCO/FIFO) | бизда бор, QBO'да йўқ | Multi-warehouse кенгайтмасининг қисми (QBO Advanced доим FIFO, танлов йўқ) |
| Preferences.AutoApplyPayments (:12310) | ЙЎҚ - customer payment фақат ҚЎЛДА allocation | ОНГЛИ ФАРҚ (Otabek-013, 2026-07-10): AutoApplyCredit билан бир оила (§8) - auto-apply 1.1 ғояси; vendor BillPayment'га бу preference QBO'да ҳам тегишли эмас |
| CustomTxnNumbers (Finance.xsd:12118-12124) | ЙЎҚ - DocNumber ДОИМ сервер sequence'идан (DocumentSequenceService) | ОНГЛИ ФАРҚ (Otabek-014, ҳукм 2026-07-14): қўлда/ташқи ҳужжат рақами киритилмайди - ноёблик ва race-ҳимоя sequence'да марказлашган; зарурат чиқса preference + ноёблик валидацияси алоҳида карта бўлади. §8-13 даги DocNumber «Мос» баҳолари шу фарқ доирасида ўқилсин (рақам формати мос, киритиш эркинлиги эмас) |

---

## 8. CreditMemo (кредит-нота)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DocNumber, TxnDate | `cmNumber` (CM-), `cmDate` | Мос (document_sequence) |
| CustomerRef | `customerId` | Мос |
| Line (SalesItemLineDetail + ClassRef) | `credit_memo_line` (item/qty/нарх/ҚҚС snapshot + class_id) | Мос - class сатрда (class-tracking.md) |
| RemainingCredit (:10506) | `open_balance` денорм | Мос ечим |
| CurrencyRef + ExchangeRate | Money (amount/base/rate) | Мос |
| Қўллаш: ReceivePayment ичида credit line | алоҳида `credit_application` + apply/unapply | Мос семантика, бошқа механика (тўловсиз тўғри қўллаш ҳам бор) |
| Preferences.AutoApplyCredit (:12301, default ёқиқ) | ЙЎҚ - фақат ҚЎЛДА apply | ОНГЛИ ФАРҚ (Otabek-009, 2026-07-08): auto-apply 1.1 ғояси, roadmap'да |

## 9. RefundReceipt (пул қайтариш чеки)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DocNumber, TxnDate, CustomerRef | `rrNumber` (RR-), `rrDate`, `customerId` | Мос |
| DepositToAccountRef (:9726) | `bank_account_id` (BANK туридан, валюта мос) | Мос - пул счётидан тўғри чиқади, AR/application ЙЎҚ (иккисида ҳам) |
| Line | `refund_receipt_line` (CM кўзгуси) | Мос |
| PaymentMethodRef | йўқ | Кичик қолдиқ - PaymentMethod каталоги бор (036), RR'га улаш керак бўлса кейин |

## 10. VendorCredit (таъминотчи кредити)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DocNumber, TxnDate, VendorRef | `vcNumber` (VC-), `vcDate`, `vendorId` | Мос |
| Line (ItemBased + AccountBased) | `vendor_credit_line` (ITEM/EXPENSE) | Мос - иккала сатр тури бор |
| Қўллаш: BillPayment ичида credit line | алоҳида `vendor_credit_application` + apply/unapply | Мос семантика, бошқа механика (CM билан изчил) |
| CurrencyRef + ExchangeRate | Money | Мос |

## 11. Estimate (таклиф)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DocNumber, TxnDate, CustomerRef | EST- рақам, сана, мижоз | Мос |
| TxnStatus (:4665 - Pending/Accepted/Closed/Rejected) | `status` (PENDING/ACCEPTED/REJECTED/CLOSED) | Мос |
| ExpirationDate (:5640) | `expiration_date` (Estimate.java, форма + spec estimates-po.md:19) | Мос - бор (Otabek-015 тузатуви 2026-07-10: аввал «йўқ» деб хато ёзилган эди) |
| AcceptedBy/AcceptedDate (:5649) | йўқ (status ўтишида ким/қачон сақланмайди) | Кичик қолдиқ - зарурат чиқса қўшилади |
| LinkedTxn (Invoice) | `invoice_id` FK + айлантириш оқими | Мос - конверсия prefill + манба CLOSED |
| GL таъсири | ЙЎҚ (иккисида ҳам non-posting) | Мос - PostingService import қилинмайди (тест билан) |

## 12. PurchaseOrder (буюртма)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DocNumber, TxnDate, VendorRef | PO- рақам, сана, таъминотчи | Мос |
| POStatus (:10436 - Open/Closed) | `status` (OPEN/CLOSED) | Мос |
| LinkedTxn (Bill) | `bill_id` FK + айлантириш | Мос |
| GL таъсири | ЙЎҚ (иккисида ҳам) | Мос |

## 13. SalesReceipt (сотув чеки)

| QBO майдони | Бизда | Изоҳ |
|---|---|---|
| DocNumber, TxnDate | SR- рақам, `srDate` | Мос |
| CustomerRef (ИХТИЁРИЙ) | `customerId` МАЖБУРИЙ (BR-SR-001) | ОНГЛИ ФАРҚ (2026-07-08, Arbitr-045): Statement/ҳисобот қиймати учун; чакана оқимда сўралса бўшатилади |
| DepositToAccountRef (:9008) | `bank_account_id` (BANK/CASH, валюта ҳужжатга тенг - BR-SR-002) | Мос - AR қатнашмайди (иккисида ҳам) |
| Line + ClassRef | `sales_receipt_line` + class_id | Мос (invoice кўзгуси) |
| PaymentMethodRef (:9008 ёни) | йўқ | Кичик қолдиқ - Expense'даги каби улаш мумкин, зарурат чиқса |

## 14. Текшириш тартиби (кейинги сессиялар учун)

1. Schema саволи туғилди - аввал шу ҳужжат, кейин XSD'нинг ўзи:
   `grep -A 30 'name="Invoice"' docs/qbo-reference/Finance.xsd`
2. Майдон изоҳидаги `Product:` белгисига қаралади - `QBW` бўлса
   эталонга кирмайди.
3. Расмий веб-ҳужжат (developer.intuit.com) фақат матний тушунтириш
   керак бўлганда ўқилади - schema масаласида XSD устун.
4. QBO'да йўқ нарса қўшилаётган бўлса - у multi-warehouse inventory
   кенгайтмасига тегишлими? Тегишли бўлмаса - ҚЎШИЛМАЙДИ, аввал
   фойдаланувчи билан келишилади.
