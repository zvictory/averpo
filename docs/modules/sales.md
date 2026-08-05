# Sales - Invoice / InvoicePayment (7-босқич) - SPEC

## Мақсад
Сотув оқими: Invoice (DRAFT→POSTED→REVERSED) - омбордан чиқим (COGS)
- AR - InvoicePayment (allocation билан, аванс рухсат) - AR aging.
Purchases (6-босқич)нинг КЎЗГУ АКСИ - тайёр паттернлар устига
қурилади; проводкалар: docs/posting-rules.md «Сотув» жадвали.
Боғлиқ спецлар: қайтаришлар (CreditMemo/RefundReceipt) -
docs/modules/returns.md; таклиф (Estimate) -
docs/modules/estimates-po.md; ҚҚС - docs/modules/tax.md; сотув чеки
(SalesReceipt) ҳам шу модулда - posting-rules «Сотув чеки» бўлими.

## Қатъий қарорлар (2026-07-06 тасдиқланган)
- **Ҳамма item тури сотилади** (QBO услуби): INVENTORY сатр - омбордан
  issue + COGS; SERVICE / NON_INVENTORY - омборсиз, фақат даромад.
- **Қайтариш ҳужжатлари**: docs/modules/returns.md (қурилган -
  CreditMemo/RefundReceipt, BR-RET-001..008; posting-rules.md
  «Қайтариш (Returns)» бўлими). Майда тузатиш учун Invoice/
  InvoicePayment reverse ҳам жойида.
- **Credit limit - огоҳлантириш, тўсмайди** (QBO услуби): AR қолдиғи +
  янги invoice лимитдан ошса форма/кўришда белги, лекин post блокланмайди.
- **InvoicePayment purchase паттерни билан айнан бир хил**: allocation
  тўғридан-тўғри invoice'га FK, аванс (unallocated) рухсат, DRAFT'сиз
  (яратилди = POSTED), тўлов валютаси = invoice валютаси (BR-RCPT-006),
  realized курс фарқи ҳар allocation учун алоҳида JE.
- **Тўлов қабул счёти танланади**: банк / касса / UNDEPOSITED_FUNDS
  (BANK туридан ёки UNDEPOSITED_FUNDS detail type - BR-RCPT-002).

## Entity'лар (changeset 021, sales модули)

### Invoice (invoice)
| Майдон | Тип | Изоҳ |
|---|---|---|
| invoice_number | varchar(20) unique | DocumentSequence INV-2026-NNNNN (draft'да ҳам) |
| customer_id | UUID (DB FK contact) | CUSTOMER типдаги фаол контакт (BR-INV-001) |
| bill_date / due_date | date | invoice_date; due_date мижоз тўлов шартидан автоматик |
| currency | ManyToOne Currency | Ҳужжат валютаси (қоида №11). Контактдан олинади (Contact.currency), ҳужжатда ўзгартирилмайди - QBO қатъий, Arbitr-087 (BR-SINV-011); тафсилот multi-currency.md |
| exchange_rate | numeric(24,12) | Ҳужжат курси; home'да 1 |
| status | enum | DRAFT / POSTED / REVERSED |
| total / total_base | numeric(19,4) | Ҳужжат / home валютада |
| paid_amount / balance_due | numeric(19,4) | Денормализация (ҳужжат валютасида) |
| payment_status | enum | UNPAID / PARTIAL / PAID |
| memo | varchar(500) | |
| posted_at | timestamptz | |

Изоҳ: Bill'даги vendor_invoice_number ва partial unique guard'нинг
Invoice'да КЕРАГИ ЙЎҚ (invoice_number ўзимизники, unique). Bill'дан
шу жиҳат билан фарқ қилади.

### InvoiceLine (invoice_line)
| Майдон | Тип | Изоҳ |
|---|---|---|
| line_no | int | |
| type | enum | ITEM / SERVICE |
| item_id | UUID nullable (FK item) | Ҳар икки турда ҳам item танланади |
| warehouse_id | UUID nullable (FK warehouse) | ITEM (INVENTORY item): мажбурий; SERVICE: null |
| quantity | numeric(19,4) | Мусбат |
| unit_price | numeric(24,12) | Сотув нархи (ҳужжат валютасида) |
| income_account_id | UUID (FK account) | Даромад счёти (item'дан default, ўзгартириш мумкин) |
| amount | numeric(19,4) | qty × unit_price (ҳужжат валютасида) |
| memo | varchar(500) | |

Изоҳ: сатр тури ITEM (INVENTORY item = омбордан issue + COGS) ёки
SERVICE (NON_INVENTORY/SERVICE item = даромадгина, омборсиз). Тур
item'нинг ItemType'идан келиб чиқади - форма item танланганда белгилайди.

### InvoicePayment (invoice_payment) - тушум
receipt_number (RCPT-2026-NNNNN), customer_id, payment_date,
deposit_account_id (банк/касса/UNDEPOSITED_FUNDS, BR-RCPT-002),
currency + exchange_rate, total_amount / allocated_amount /
unallocated_amount (денормализация), status (POSTED/REVERSED), memo.

### InvoicePaymentAllocation (invoice_payment_allocation)
payment FK + invoice FK + amount, UNIQUE(payment_id, invoice_id).

## Posting (posting-rules «Сотув» - қатъий)
- **Invoice post** (sourceModule=INVOICE, docId=invoice id): ҳар сатр -
  AR тизим счётига Dt (жами, ҳужжат курсида), item даромад счётига Cr
  (ҳужжат курсида). ITEM (INVENTORY) сатрлар ҚЎШИМЧА:
  `InventoryService.issue` (омбордан, home таннарх) → COGS
  (SUPPLIES_MATERIALS_COGS) Dt / item INVENTORY asset Cr, home валютада.
  Нол таннарх (avg 0) - COGS сатр ёзилмайди. SERVICE сатр омборга
  тегмайди.
- **Invoice reverse**: COGS сатрлари учун товар омборга ҚАЙТАДИ
  (`InventoryService.reverseIssue` - асл ейилган партиялар/қиймат
  ортга), кейин GL сторно (reverseBySource). Ҳимоя керак эмас -
  reverseIssue доим бажарилади (товар қайта кирими).
- **Payment post** (sourceModule=INVOICE_PAYMENT): танланган қабул
  счёти Dt (тўлов валютасида, тўлов курсида) / AR Cr. Аванс қисми ҳам
  шу ёзув ичида.
- **Realized курс фарқи - ҳар allocation учун алоҳида JE**
  (sourceModule=RECEIPT_ALLOCATION, docId=allocation id): фарқ base =
  allocation × (тўлов курси - invoice курси); мусбат (тўловда base
  кўпроқ келди) - фойда: AR Cr / EXCHANGE_GAIN_OR_LOSS... йўқ, аниқ:
  posting-rules «Сотув» - фойда: AR Dt / gain Cr, зарар: gain Dt /
  AR Cr. Нол фарқ - JE ёзилмайди.
- **Payment reverse**: GL сторно + allocation'лар бекор (invoice
  paid/status қайта) + FX JE'лари сторно.

### Курс фарқи йўналиши (Bill билан ТЕСКАРИ - диққат)
Сотувда AR - актив (мижоз қарзи). Invoice base'да AR дебети ёзилган.
Тўлов base'да фарқли келса:
- Тўлов курси > invoice курси (base кўпроқ олдик) - realized ФОЙДА:
  фарқни AR'га Dt қилиб (қолдиқни тенглаш) EXCHANGE_GAIN_OR_LOSS Cr.
- Тўлов курси < invoice курси - realized ЗАРАР: EXCHANGE_GAIN_OR_LOSS
  Dt / AR Cr.
formula: diffBase = allocation × (paymentRate - invoiceRate).

## Валидация (BR-SINV, BR-RCPT)
Диққат: инвойс коди **BR-SINV-*** (Sales INVoice) - BR-INV-* эса
инвентарь (inventory) учун аллақачон банд, чалкашмаслик учун.
| Код | Қоида |
|---|---|
| BR-SINV-001 | Customer (CUSTOMER типдаги фаол контакт) шарт |
| BR-SINV-002 | Камида битта сатр |
| BR-SINV-003 | Сатр миқдори/нархи мусбат |
| BR-SINV-004 | ITEM сатрида INVENTORY item + омбор; омборда қолдиқ етарли |
| BR-SINV-005 | Даромад счёти INCOME туркумидан, фаол ва postable |
| BR-SINV-006 | Фақат DRAFT таҳрирланади/post қилинади |
| BR-SINV-007 | Фақат POSTED reverse қилинади |
| BR-SINV-008 | Чет валюта invoice'ида мусбат курс шарт; home'да 1 |
| BR-SINV-009 | Invoice санаси шарт |
| BR-SINV-010 | Сотилаётган item фаол бўлиши шарт |
| BR-SINV-011 | Invoice валютаси мижоз валютасига мос бўлиши шарт (Contact.currency, null = home; QBO қатъий, Arbitr-087) - server валютани контактдан ЎЗИ олади |
| BR-RCPT-001 | Тўлов суммаси мусбат |
| BR-RCPT-002 | Қабул счёти банк/касса/UNDEPOSITED_FUNDS, фаол ва postable |
| BR-RCPT-003 | Allocation фақат POSTED invoice'га |
| BR-RCPT-004 | Allocation invoice қолдиғидан ошмайди |
| BR-RCPT-005 | Allocation'лар йиғиндиси тўлов суммасидан ошмайди |
| BR-RCPT-006 | Тўлов валютаси invoice валютаси билан бир хил (MVP) |
| BR-RCPT-007 | Фақат POSTED тўлов reverse қилинади |
| BR-RCPT-008 | Тўлов санаси шарт |
| BR-RCPT-009 | Allocation ўша customer'нинг invoice'ига |
| BR-RCPT-010 | Customer (CUSTOMER типдаги фаол контакт) шарт |
| BR-RCPT-011 | Бир тўловдан бир invoice'га биттагина allocation (DB unique) |
| BR-RCPT-012 | Чет валюта тўловида мусбат курс шарт; home'да 1 |
| BR-RCPT-013 | Allocation фақат POSTED тўловдан |

## InventoryService янги API (reverseIssue)
`reverseIssue(movementId, date)` - чиқим ҳаракатини АЙНАН ортга
қайтаради (Invoice reverse учун): FIFO'да шу issue'нинг consumption
изи (findByMovementId) бўйича ҳар партияга ейилган qty қайтарилади
(layer.remaining += qty, exhausted қайта), balance қайта; AVCO'да
qty += issue qty, value += issue total_cost, ўртача қайта. Янги IN
reversal movement ёзилади (аудит). Bill'даги reverseReceive'нинг
жуфти - лекин бу ерда ҳимоя шарт эмас (товар қайта кирими доим мумкин).

## Туртки режаси (тасдиқлансагина бошланади)
1. Spec + BR каталог (BR-SINV, BR-RCPT) + changeset 021 + domain
   entity'лар (Invoice, InvoiceLine, InvoicePayment, allocation +
   enum'лар) + DocumentType.INVOICE/RECEIPT + document_sequence seed.
2. InvoiceService: draft CRUD, post (GL AR/income + INVENTORY сатрлар
   issue+COGS), reverse (reverseIssue + reverseBySource), credit limit
   огоҳлантириш - тўлиқ тестлар. InventoryService.reverseIssue + тест.
3. InvoicePaymentService: post + allocation + FX-per-allocation +
   reverse, денормализация - тўлиқ тестлар.
4. UI: /invoices рўйхат/форма/кўриш, тўлов формаси (очиқ invoice'лар),
   AR aging экрани (/reports/ar-aging), sidebar СОТУВ бўлими,
   credit limit белгиси.

## Тестлар (мажбурий рўйхат - 2-3-турткиларда)
- Invoice post: AR дебети = total_base; ҳар сатр даромади тўғри
  счётга; INVENTORY сатр омбордан чиқди + COGS Dt/INVENTORY Cr
  (таннарх home). SERVICE сатр омборга тегмайди.
- Чет валюта invoice: base = amount × rate (BR-SINV-008).
- Reverse: GL сторно + товар омборга қайтди (омбор қолдиғи ва қиймат
  асл ҳолига); COGS ҳам сторно.
- Омборда қолдиқ етмаса BR-SINV-004 (post блокланади).
- Credit limit ошса огоҳлантириш келади, лекин post ўтади.
- Payment: қабул счёти Dt, AR Cr; денормализация (UNPAID→PARTIAL→PAID);
  аванс unallocated'да.
- FX: invoice 12 600, тўлов 12 700 - allocation фарқи фойда (AR Dt/
  gain Cr), 12 500 - зарар (иккала йўналиш), нол фарқда JE йўқ.
- Payment reverse: allocation'лар бекор, invoice status қайтади,
  FX JE'лар сторно.
- BR-RCPT-003/004/005/006/009 guard'лари.

## Экранлар (4-туртки)
Sidebar СОТУВ бўлимига: Invoice'лар (/invoices), Тушумлар
(/invoice-payments). AR aging (/reports/ar-aging) - Ҳисоботлар
бўлимида (Nargiza-002: QBO эталонида ҳамма ҳисоботлар марказда).
«+ Янги»: Invoice, Тушум. Ҳамма жадвал zebra + .table-wrap, 375px,
money формат.
