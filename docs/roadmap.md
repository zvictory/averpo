# Қуриш кетма-кетлиги (босқичлар)

Ҳар босқич охирида: тестлар ўтади + вертикал кесимда ишлайдиган тизим.
Эски лойиҳадан олинадиган schema ғоялари: docs/old-erp-ideas.md.

## 1-босқич - Skeleton (шу zip'да тайёр)
Лойиҳа, Gradle, Liquibase, BaseEntity, Money, JTE layout, docker-compose.

## 2-босқич - Ledger ядроси
- CompanySettings (компания номи + home currency, default UZS)
- Account CRUD экрани (иерархик CoA, IFRS/QuickBooks услуб
  стандарт режа CSV'дан импорт + ўз CSV файлини юклаш)
- JournalEntry/Line, PostingService (post + reverse) тўлиқ тестлар билан
- Қўлда проводка киритиш экрани
- Trial Balance ҳисоботи
- Opening balance + CoA balance устуни
Натижа: тизимда қўлда бухгалтерия юритса бўлади.

## 2.5-босқич - Пойдевор + QBO UI пакети (барча банд бажарилган)
Тартиби фойдаланувчи билан келишилган (task #22-27):
1. BusinessRule марказий каталог enum (код + HTTP status + хабар битта
   жойда), BusinessRuleException enum қабул қилади
2. Money формат: минг ажратгич (кўрсатиш + киритиш) - доимий қоида
3. CoA'да CSV импорт уч нуқта (⋮) actions менюга
4. Sidebar QBO Online услубида
5. Глобал «+ Янги» тугмаси (QBO + New)
6. Роллар (жорий тизим: 8 роль - user-roles.md)

## 3-босқич - Contacts + Items экранлари (task #28)
Customer/Vendor CRUD, Item (товар/хизмат), ItemCategory, Unit экранлари -
янги UI пакет намунасида.

## 3.5-босқич - Бухгалтерия асослари (task #29-31)
- DocumentSequence: умумий ҳужжат рақамлаш (INV/BILL/PAY/JE) (docs/modules/document-sequence.md; JE рақами document_sequence'га кўчди)
- Closing date lock (QBO услуби, эски fiscal_period соддалаштирилган)
  (docs/modules/closing-date.md, BR-LED-020)
- Contact кенгайтириш: манзиллар, шахслар, банк, credit limit, ИНН
  (contact.md «Кенгайтма», BR-CON-005..010, changeset 016)

## 4-босқич - Multi-currency
Кўлами (аниқлаштирилган): курс КАТАЛОГИ ва унинг етказилиши - ExchangeRate
CRUD + ЦБ импорти (scheduler кунига 2 марта + қўлда), /settings/currencies
экранлари, JE/OB формаларида курс prefill, курс фарқи ҚОЛИПИНИНГ posting
тести. Тўлиқ realized курс фарқи (ҳужжат/тўлов оқими билан) 6-7-босқичда
киради. docs/modules/multi-currency.md; BR-FX-001..004, BR-CUR-003.

## 4.5-босқич - Detail type номларини QBO расмийсига тузатиш
7 та AccountDetailType константаси расмий AccountSubTypeEnum номларига
мослаштирилди + PAYROLL_TAX_PAYABLE қўшилди (changeset 018 data
migration билан; UNEARNED_REVENUE қаторлари OTHER_CURRENT_LIABILITIES
турига ўтди). Режа: docs/qbo-reference/detail-type-rename-plan.md.

## 5-босқич - Inventory (task #32)
Warehouse, StockMovement, StockBalance, valuation service'лари:
AVCO ва FIFO (cost layer + consumption, old-erp-ideas.md §6) - иккаласи
тўлиқ тестлар билан, метод CompanySettings'дан олинади.
InventoryValuationLock имплементацияси (биринчи ҳаракатдан кейин метод
қулфи). Transfer, Adjustment.
Бажарилди: docs/modules/inventory.md - changeset 017, InventoryService
(receive/issue/adjust/transfer, adjustment GL проводкаси posting-rules
бўйича), BR-WH-001/002 + BR-INV-001..010 + BR-LED-021, экранлар:
қолдиқлар/ҳаракатлар/тузатиш/кўчириш + /settings/warehouses,
sidebar «ОМБОР» бўлими. Receive/Issue GL'и Bill/Invoice билан
6-7-босқичда уланади.

## 6-босқич - Purchases (task #33 нинг Bill қисми)
Bill (DRAFT-POSTED) - inventory кирим - AP - BillPayment - AP aging.
Vendor duplicate guard: (vendor, vendor_invoice_number) partial unique.
PaymentAllocation: битта тўлов бир нечта bill'га.
Landed cost: харид харажатини (ташиш, божхона) receipt'ларга
тақсимлаш, клиринг счёти орқали (posting-rules.md га қаранг).
Бажарилди: docs/modules/purchases.md - changeset 019/020, BillService
(draft CRUD/post/reverse - омбор кирими айнан ўз нархида қайтади),
BillPaymentService (аванс + realized курс фарқи ҳар allocation учун
алоҳида JE + reverse), LandedCostService (клирингдан эркин сумма,
қиймат нисбатида, сотилган улуш COGS'га, аниқ reverse), AP aging.
BR-BILL-001..012, BR-PAY-001..013, BR-LC-001..007. Экранлар: /bills,
/payments, /landed-costs, /reports/ap-aging, sidebar ХАРИД бўлими.

## 7-босқич - Sales (task #33 нинг Invoice қисми)
Invoice - COGS (AVCO/FIFO) - AR - InvoicePayment (allocation билан) -
AR aging.
Бажарилди: docs/modules/sales.md - changeset 021, InvoiceService
(draft CRUD/post - AR/даромад + ITEM сатрларга омбордан issue+COGS,
SERVICE омборсиз; reverse - reverseIssue билан товар айнан ейилган
партияларга қайтади, BR-INV-009 гарови; credit limit огоҳлантиради,
тўсмайди), InvoicePaymentService (аванс + realized курс фарқи ҳар
allocation учун алоҳида JE, йўналиши AP'га тескари + reverse;
UNDEPOSITED_FUNDS қабул счёти), AR aging. BR-SINV-001..010,
BR-RCPT-001..013. Экранлар: /invoices (сотув нархи prefill, credit
limit белгиси), /invoice-payments, /reports/ar-aging (drill-down),
sidebar СОТУВ бўлими.

## 8-босқич - Banking (task #34)
BankAccount, транзакциялар, конверсия, reconciliation
(bank_statement + line, old-erp-ideas.md §5).
  Бажарилди: docs/modules/banking.md - changeset 022,
BankTransactionService (кўп сатрли DEPOSIT / EXPENSE / TRANSFER,
конверсияда base фарқи EXCHANGE_GAIN_OR_LOSS'га, валюта банк
счётидан, DRAFT'сиз + reverse), ReconciliationService (QBO Reconcile
модели - кўчирма сатрларисиз: давр + якуний қолдиқ, GL сатрлари
белгиланади, фарқ 0 да COMPLETED; opening занжири; match'лар bank
модулида, ledger'га фақат public read - reconcilableLines).
BR-BT-001..009, BR-RCN-001..008. Экранлар: /bank-transactions
(тур бўйича динамик форма), /reconciliation (checkbox + жонли фарқ),
sidebar БАНК бўлими. Бонус: Uuid7 монотонлик тузатиши (FIFO flake).

## 9-босқич - Reports
Balance Sheet, P&L, inventory valuation, dashboard.
  Бажарилди: docs/modules/reports.md - changeset 024
(молия йили бошланиш ойи, BR-SET-004) ва 025 (LC reversal_date).
BalanceSheetService (QBO тузилма, RE/NI виртуал бўлиниши молия йили
бўйича, актив == мажбурият + капитал текшируви), ProfitAndLossService
(Даромад → COGS → Ялпи → Харажат → Операцион → Бошқа → Соф фойда),
InventoryValuationService (санага тиклаш, LandedValueContribution
порти - LC улушлари сторно ойнаси билан, GL INVENTORY солиштируви),
LedgerDashboardService + dashboard модули. Экранлар:
/reports/balance-sheet, /reports/profit-loss (давр preset'лари,
drill-down), /reports/inventory-valuation (омбор фильтри/кесими),
`/` энди QBO home услубидаги dashboard (P&L карта + 6 ойлик SVG
график, банк қолдиқлари ўз валютасида, AR/AP очиқ ва муддати ўтган,
харажатлар топ-5). Sidebar ҲИСОБОТ янгиланди.
Қолди (кейинги навбатга): AR/AP aging тарихий as-of реконструкцияси
(IFRS-004 2-босқичи: asOf'гача post қилинган ҳужжатлар минус
asOf'гача allocation'лар; ҳозирча aging фақат жорий санага -
BR-RPT-001). Cash basis toggle - «Кейинги босқичлар»да.

## UoM босқичи - Бирлик гуруҳлари ва конверсия (task #37)
Бажарилди: docs/modules/uom.md - changeset 026 (unit_group, unit'да
group/factor/is_base - гуруҳда битта base partial unique билан, item'да
default харид/сотув бирликлари), 027/028 (bill_line/invoice_line'да
unit_id + unit_factor SNAPSHOT - каталог кейин ўзгарса тарихий ҳужжат
бузилмайди). UnitService: гуруҳ CRUD, инвариантлар (BR-UOM-001..006),
toBase/factorBetween/selectableUnits; ItemService BR-ITM-012. Bill/
Invoice: сатр киритилган бирликда (сумма qty × нарх - GL ўзгармаган),
омбор ҳаракати base миқдорда (qty × factor snapshot, scale 4), reverse
movement орқали аниқ. Экранлар: /settings/units (гуруҳлар + factor/
base), item формасида default бирликлар (гуруҳга JS фильтр), bill/
invoice сатрида бирлик select + нарх prefill (каталог нарх × factor),
кўришда миқдор бирлиги билан. Омбор/valuation/ҳисоботлар ўзгаришсиз -
улар доим base'да эди.

## PriceList босқичи - Нарх рўйхатлари (task #38)
Бажарилди: docs/modules/price-list.md - changeset 031 (price_list,
price_list_item, price_list_customer; янги `pricing` модули). Поғонали
(item, min_quantity)→нарх, мижоз бириктируви РЎЙХАТ томонида (QBO Price
rules услуби - contact↔pricing доиравий боғлиқлик йўқ), битта default
рўйхат (partial unique). PriceListService.resolvePrice(мижоз рўйхати→
default; валюта+давр+active текшируви; поғонадан min_qty<=baseQty энг
каттаси) - фақат PREFILL, ҳужжатга ҳавола сақланмайди, GL/posting'га
таъсир йўқ. BR-PL-001..008. Экранлар: /settings/price-lists (рўйхат +
карта: сарлавҳа/поғоналар/мижоз бириктириш) + settings каталог ҳаволаси.
Invoice формаси: GET /price-lists/lookup (/settings дан ТАШҚАРИ -
ACCOUNTANT ҳам, CurrencyController.lookup прецеденти) + JS prefill (мижоз/
item/миқдор/бирлик ўзгарганда рўйхатдан нарх × factor; data-autofill
белгиси қўлда терилган нархни сақлайди, поғонадан пастга тушса тозалайди).
DiscountRule (фоиз/фикс чегирма) АТАЙЛАБ кейинги босқичга.

## Tax босқичи - ҚҚС каталоги ва ҳужжат солиғи (task #35)
Бажарилди: docs/modules/tax.md + posting-rules.md «Солиқ» бандлари -
changeset 032 (tax_rate + item/bill/invoice/line колонкалар + seed
QQS12/NO_TAX). TaxRateService (CRUD + BR-TAX-001..005 + documentRateValue
snapshot), TaxAmounts (net/tax бўлиниши - exclusive/inclusive, аниқ
комплемент). Bill/Invoice: сарлавҳа amounts_inclusive + сатр ставка
SNAPSHOT (каталог ўзгарса тарихий ҳужжат бузилмайди), amount=НЕТТО,
gross=net+tax; GL - битта контрол счёт SALES_TAX_PAYABLE (сотувда Cr,
харидда Dt) ставка кесимида, чет валюта penny rounding MoneyAllocation
орқали (net'лар+ҚҚС'лар→AR/AP gross). Харид ҚҚСи таннархга кирмайди
(омборга нетто). Item sales/purchase default ставка (форма prefill).
Экранлар: /settings/tax-rates, Bill/Invoice форма (режим + сатр
ставка + жонли Оралиқ/ҚҚС/Жами), кўриш (ҚҚС устуни). BR-TAX-001..005.

## AuditLog босқичи - аудит журнали
Бажарилди: docs/modules/audit-log.md тўлиқ - changeset 034 (audit_event,
append-only: update/delete API умуман йўқ, индекслар created_at DESC /
event_type / username). Модул com.averpo.erp.audit: AuditEvent,
AuditEventType (JE_POSTED, JE_REVERSED, LOGIN_SUCCESS, LOGIN_FAILURE,
LOCKOUT, USER_CREATED, USER_UPDATED, PASSWORD_CHANGED), AuditLogService
(record - ягона ёзиш йўли, page - Specification филтрлари). Ledger
ҳодисалари Spring event орқали (қоида №6: event record'лари
ledger.service ичида, PostingServiceImpl post/reverse'да publish, audit
СИНХРОН тинглайди - rollback'да аудит ёзуви ҳам йўқолади, тест билан
исботланган). Auth: AuthAuditListener (Security event'лари, IP билан),
LOCKOUT LoginAttemptListener'дан, USER_* UserService'дан. Экран
/audit-log (USERS соҳаси, SUPER_ADMIN): филтрлар (сана/тур/username)
+ page/size
пагинация, сайдбар Созламалар гуруҳида. BR-* кодлари ЙЎҚ (spec қарори),
posting-rules.md ўзгармади. Кейин (2-босқич): CATALOG_* ҳодисалари,
CompanySettings ўзгариши, before/after diff, retention.

## Expense экрани + PaymentMethod (DEC-033)
QBO паритети: dedicated /expenses (рўйхат/форма/кўриш, QBO Expense
тартиби - Payee, тўлов счёти + жонли Balance, катта AMOUNT, курс
сатри, тўлов усули, Ref no, Total икки валютада), PaymentMethod
каталоги (changeset 036: name+active, seed 3 усул; Type атайлаб йўқ -
credit card РАД) + bank_transaction.payment_method_id/ref_no. Generic
банк формаси фақат Кирим бўлиб қолди (?type=EXPENSE redirect).
Кирим (Bank deposit) алоҳида экрани - кейин (фойдаланувчи қарори).

## UI етуклик пакети (/08: DEC-023/024/025/029/031/035/036)
Фойдаланувчи қарори: транзакция формалари FULL (QBO'га мос), каталог
формалари (счёт/item/contact) ўнгдан drawer'да - shared/drawer қолипи
(HTMX partial, JS'сиз fallback). CoA полиши (Number устуни, Balance
валюта билан, кўринган «Счёт амаллари»). Транзфер UI якуни (dedicated
view, кросс-валюта кўрсатишлар, ҳамма BS счётда Balance, x-cloak).
Курс prefill refactor (data-autofill, сана/валюта ўзгаришига мос,
8 форма) + каталог нарх fallback валюта қоровули. Dashboard бойитиш
(DEC-036): cash flow, охирги 30 кун тўловлари, тез амаллар,
inventory карталари + drill-down (тафсилот: reports.md Dashboard).

## Class tracking босқичи - Йўналиш кесими
Бажарилди: docs/modules/class-tracking.md тўлиқ - changeset 037
(txn_class + UNIQUE NULLS NOT DISTINCT (parent_id, name);
company_settings.track_classes DEFAULT OFF; journal_entry_line/
invoice_line/bill_line/bank_transaction_line'га class_id +
idx_jel_class). Shared: TxnClass (Java номи - Class reserved),
TxnClassService (дарахт/«Ота:Бола», BR-CLS-001/002/003, delete йўқ),
ClassTrackingMode (OFF/PER_TXN/PER_LINE - режим фақат UI'ни
бошқаради, class ҳар доим САТРДА). /settings/classes каталоги +
режим танлагичи. Posting: JournalEntryRequest.Line.classId,
BR-CLS-001 draft'да ва post'да (reverse'да ЭМАС - тарихий тег
кўчади), сторно class'ни айнан кўчиради; назорат (AR/AP/банк жами)
ва техник (FX, жамланган ҚҚС) сатрлар class'сиз - posting-rules.md
«Class кўчиши» банди. Формалар (invoice/bill/deposit-expense/JE):
PER_LINE - сатр select, PER_TXN - сарлавҳа select (controller
тарқатади), OFF - кўринмайди. /reports/profit-and-loss-by-class:
устун ҳар ишлатилган class + «Кўрсатилмаган» + Жами - устунлар
йиғиндиси айнан оддий P&L (тест билан). UI label: уз «Йўналиш»,
ru «Класс», en «Class». Кейин (2-босқич): warn-when-no-class,
inventory adjustment/LC сатрлари, Budget by Class.

## Кейинги навбат
Жорий навбат ЯГОНА манбада: review/NAVBAT.md (арбитр юритади).
Бажарилганлар: шу файлнинг бўлимлари ва docs/review-log.md.

## Хавфсизлик
Spring Security + form login + CSRF. **Роллар
(DEC-092, changeset 052): 8 роль (SUPER_ADMIN, DIRECTOR_ADMIN,
CHIEF_ACCOUNTANT, ACCOUNTANT, SALES_MANAGER, PURCHASE_MANAGER,
WAREHOUSE_MANAGER, VIEWER_AUDITOR), рухсат рольга эмас СОҲАга
(hasAuthority permission) - спец docs/modules/user-roles.md.**
Admin пароли AVERPO_ADMIN_PASSWORD env'дан (prod'да мажбурий).
Settings соҳаси SUPER_ADMIN.

User management : docs/modules/user-management.md -
changeset 029 (lockout майдонлари) + 030 (created_by БАРЧА жадвалда).
UserService (/users CRUD, парол алмаштириш оқимлари, BR-USR-001..010),
login lockout (5 хато → 15 дақиқа), createdBy аудит майдони. Профиль (DEC-101, changeset 057+066): /profile саҳифаси
(аватар, шахсий майдонлар, парол блоки, employee улаш,
must_change_password оқими). Кейин: 102 2FA TOTP → 103 Telegram
(карталар NAVBAT'да); auth-security-policy.md спец (lockout
эскалация + парол муддати) тасдиқ кутмоқда.

## Кейинги босқичлар (ҳозир қилинмайди - 1.1+ ғоялар рўйхати)
Unrealized курс фарқи (IFRS-006), multi-tenant, BS/P&L cash basis
toggle ва таққослаш устунлари (QBO customize), DiscountRule
(DEC-019 огоҳлантириши: QBO Online'да йўқ - кўлам алоҳида
келишилади), AutoApplyCredit преференцияси (QBO-009: QBO default
кредитни автоматик қўллайди - бизда онгли равишда фақат қўлда,
entities.md §8), Transfer'га ClassRef + Balance Sheet by Class
(QBO-008: онгли фарқ ҳужжатланди - зарурат чиқса). Service хатолари i18n энди бу рўйхатда ЭМАС - 1.0
production пасига кирди (QA-001, NAVBAT 19-банд).
QBO parity қолдиқлари (DEC-017/019, унутилмаслиги учун рўйхатда):
Delayed credit/Delayed charge (GL'сиз кутувчи қаторлар), Sales order
ва Item receipt (янги QBO Online UI'да бор, XSD/API'да ҳали QBW -
омбор мавзусига яқин), Budget (Class билан «Budget vs Actuals»),
тўлиқ statutory payroll (шкалалар тарихи, отпуск/касаллик - Lite
1.0'да). ~~PaymentMethod~~ (036), ~~SalesReceipt/Statement~~ ва
~~Employee~~ - 1.0 кўламига кўчди («1.0 МАРРАСИ» 4а-4в).
РАД этилганлар (фойдаланувчи қарори - қайта таклиф
қилинмайди, SABOQLAR'да): чек, credit card оқимлари (QBO-001,
CreditCardPayment), UF/bank Deposit боғланган оқими (DEC-018).

## 1.0 МАРРАСИ (таъриф -, фойдаланувчи режаси асосида)

1.0 = қуйидагилар ТУГАГАНда (бошқа ҳеч нарса 1.0'ни тўсмайди):

1. **А-рўйхат (QBO кўлам якуни)**: Class tracking (DEC-015,
  docs/modules/class-tracking.md) , Returns -
   CreditMemo/VendorCredit/RefundReceipt (DEC-017,
  docs/modules/returns.md) , Estimate/PurchaseOrder
  (docs/modules/estimates-po.md) , IFRS ҳисобот
  тақдимоти (DEC-026) .
   А-РЎЙХАТ ТЎЛИҚ ЁПИЛДИ.
2. **Якуний катта review раунди**:/08 да қурилган барча
   янги ишлар (Expense, drawer/CoA, dashboard, Class, IFRS, Returns,
   Est/PO) устидан тўлиқ кўрик + триаж + тузатишлар.
3. **Production паси**: QA-001 (BR хабарлари i18n),
   PERF-perf1 + PERF-018 (пагинация/overfetch master),
   DEC-027 (rollback аудити - шу пасда триггери отилади).
  SEC-001 (DB парол fail-fast) .
4. **Attachments (DEC-013, docs/modules/attachments.md)**
  (changeset 042;
   қамров кенгайиши - қолган кўришлар + map - DEC-048 фикс
   тўлқинида).
4а. **Payroll Lite + аванс** (фойдаланувчи буюрди -
   QBO'дан атайлаб фарқ №2, engineering-rules.md янгиланган): Employee
   (ContactType), ойлик ҳисоблаш ҳужжати (ставкалар созламада,
   snapshot), аванс/қисман тўлаш, ведомость. Spec:
  docs/modules/payroll.md. : Employee/ставкалар (23а),
   PayrollRun (23б, PAYR- рақам), PayrollPayment + ведомость (23в) -
   учала туртки ТАСДИҚ. Ведомость GL асосига кўчиши - DEC-047
   фикс тўлқинида.
4б. **SalesReceipt** (changeset 046, SR-).
4в. **Statement** (print қатлами билан -
   DEC-040 негизи).
4г. **«+ Янги» мега-меню** (DEC-038): QBO Create
   панели услубидаги кенг кўп устунли панел - қурилган, deploy 1 да
   жонли серверда.
4д. **Глобал қидирув** (DEC-039, кейин 061 да
   сайдбарга кўчирилди; қамров кенгайиши 074): QBO Navigate паритети -
   ҳужжат рақами/контакт/товар/счёт/экран битта майдондан.
   Spec: docs/modules/global-search.md.
4ж. **Setup оқими ва фойдаланувчи қулайликлари** /10:
   биринчи кириш redirect (DEC-056), Excel бошланғич import
   (docs/modules/import-excel.md), Factory reset
   (docs/modules/factory-reset.md), контакт карточкаси
   (docs/modules/contact-card.md), аудит кенгайиши (audit-log.md),
   ҳужжатлараро линклар (063). Кросс-ҳаволалар: transfer -
   docs/modules/transfer.md, товар каталоги - docs/modules/item.md,
   кўрсатиш қоидалари - docs/modules/ui-navigation-display.md.
4е. **QBO parity тўртлиги** (фойдаланувчи: rebrand'дан
   ОЛДИН): DEC-040 ҳужжат чоп этиш/PDF/email (Statement print
   нақшидан кенгаяди), DEC-041 Recurring transactions, DEC-042
   банк CSV импорти + match, DEC-043 ҚҚС давр ҳисоботи. NAVBAT
   29-32 бандлар (D/E-тўлқинлар); 040/041/042 spec-аввал.
5. **Averpo номига тўлиқ ўтиш ** (расмий ном
  averpo.com - фойдаланувчи): пакет com.averpo.erp,
   AverpoApplication, бренд «Averpo ERP», build averpo-erp, локал
   база averpo/averpo_test, env AVERPO_ADMIN_PASSWORD. Репо/папка
   (gitlab.com/averpo/averpo-erp-web, D:\Averpo\averpo-erp-web),
  сервер миграцияси ва DEPLOY 5 (app.averpo.com + averpo.com) -. Changeset author'лар averpo (фойдаланувчи қарори).
6. **1.0 release**: build версияси 1.0.0 + roadmap/README белгиси +
   git tag v1.0.0 (фойдаланувчи қўяди).

Backlog сиёсати (1.0'га КИРМАЙДИ): тригерли ёзувлар (DEC-002/004,
PERF-013, IFRS-007) фақат триггер отилганда - махсус вақт
ажратилмайди; PERF-backlog4 МУЗЛАТИЛГАН; IFRS-006 (unrealized FX) -
фойдаланувчи алоҳида буюрса. Spec 2-босқич бўлимлари - имконият
рўйхати, мажбурият эмас.

  - ҳолати review/NAVBAT.md да юритилади.
