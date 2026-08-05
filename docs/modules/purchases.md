# Purchases - Bill / BillPayment (6-босқич) - SPEC

## Мақсад
Харид оқими: Bill (DRAFT→POSTED→REVERSED) - inventory кирим - AP -
BillPayment (allocation билан, аванс рухсат) - AP aging - landed cost.
Схема манбаи: docs/old-erp-ideas.md §4 (соддалаштиришлар қуйида),
проводкалар: docs/posting-rules.md «Харид» жадвали.
Боғлиқ спецлар: қайтариш (VendorCredit) - docs/modules/returns.md;
буюртма (PurchaseOrder) - docs/modules/estimates-po.md; ҚҚС -
docs/modules/tax.md; омбор кирими - docs/modules/inventory.md.

## Қатъий қарорлар (тасдиқланган)
- **BillPayment purchase модули ичида** (умумий payment модули эмас):
 allocation полиморф ҳаволасиз, тўғридан-тўғри bill'га FK - соддароқ
 ва тип-хавфсиз. 7-босқичда InvoicePayment алоҳида бўлади.
- **Аванс (unallocated) РУХСАТ** (old-erp услуби): тўлов total /
 allocated / unallocated денормализацияси; тақсимланмаган қисм AP'да
 vendor аванси бўлиб туради (AP дебет қолдиғи), кейин allocate
 қилинади.
- Approve workflow ЙЎҚ (old-erp'дан олинмади) - DRAFT/POSTED/REVERSED
 модели (ledger билан бир хил).
- Тўлов валютаси = bill валютаси (BR-PAY-006, MVP); курслар фарқли
 бўлиши мумкин - realized курс фарқи.
- Тўлов DRAFT'сиз: яратилди = POSTED (QBO услуби). Тузатиш - reverse.
- Allocation фақат ҚЎШИЛАДИ; олиб ташлаш - тўловни reverse қилиш
 орқали (MVP соддалиги).

## Entity'лар (changeset 019, purchase модули)

### Bill (bill)
| Майдон | Тип | Изоҳ |
|---|---|---|
| bill_number | varchar(20) unique | DocumentSequence BILL-2026-NNNNN (post'да берилади, draft'да ҳам - рақам draft яратилишида олинади) |
| vendor_id | UUID (DB FK contact) | Dimension паттерни; VENDOR типдаги контакт (BR-BILL-001) |
| vendor_invoice_number | varchar(100) nullable | **Partial unique**: (vendor_id, vendor_invoice_number) WHERE vendor_invoice_number IS NOT NULL AND status IN ('DRAFT','POSTED') - BR-BILL-006; reverse'дан кейин қайта киритиш очиқ |
| bill_date / due_date | date | due_date vendor тўлов шартидан автоматик (ўзгартириш мумкин) |
| currency | ManyToOne Currency | Ҳужжат валютаси (қоида №11). Контактдан олинади (Contact.currency), ҳужжатда ўзгартирилмайди - QBO қатъий, Arbitr-087 (BR-BILL-013); тафсилот multi-currency.md |
| exchange_rate | numeric(24,12) | Ҳужжат курси (QBO услуби - ҳужжат даражасида); home'да 1 |
| status | enum | DRAFT / POSTED / REVERSED |
| total / total_base | numeric(19,4) | Ҳужжат валютасида / home'да |
| paid_amount / balance_due | numeric(19,4) | Денормализация (ҳужжат валютасида) |
| payment_status | enum | UNPAID / PARTIAL / PAID |
| memo | varchar(500) | |
| posted_at | timestamptz | |

### BillLine (bill_line)
| Майдон | Тип | Изоҳ |
|---|---|---|
| line_no | int | |
| type | enum | ITEM / EXPENSE / LANDED_COST |
| item_id | UUID nullable (FK item) | ITEM: INVENTORY типдаги item (BR-BILL-004) |
| warehouse_id | UUID nullable (FK warehouse) | ITEM: мажбурий |
| quantity | numeric(19,4) nullable | ITEM: мусбат |
| unit_price | numeric(24,12) nullable | Ҳужжат валютасида |
| account_id | UUID nullable (FK account) | EXPENSE: EXPENSE/COGS туркумидаги фаол postable счёт (BR-BILL-005) |
| amount | numeric(19,4) | Ҳужжат валютасида; ITEM'да qty × unit_price |
| memo | varchar(500) | |

### BillPayment (bill_payment)
payment_number (PAY-2026-NNNNN), vendor_id, payment_date,
bank_account_id (BANK туридаги счёт, BR-PAY-002), currency (ManyToOne)
+ exchange_rate, total_amount / allocated_amount / unallocated_amount
(денормализация, ҳужжат валютасида), status (POSTED/REVERSED), memo.

### BillPaymentAllocation (bill_payment_allocation)
payment FK + bill FK + amount (ҳужжат валютасида),
UNIQUE(payment_id, bill_id) - бир тўловдан бир bill'га битта ёзув.

## Posting (posting-rules.md «Харид» - қатъий)
- **Bill post** (sourceModule=BILL, docId=bill id): ҳар ITEM сатр -
 item'нинг inventory asset счётига Dt (ҳужжат валютасида, ҳужжат
 курси) + `InventoryService.receive` (home қийматда: base = amount ×
 rate; reference=BILL/bill id); EXPENSE сатр - танланган счётга Dt;
 LANDED_COST сатр - INVENTORY_CLEARING тизим счётига Dt (BR-LED-021
 топилмаса); жами AP тизим счётига Cr. Idempotency/давр қулфи
 PostingService'дан автоматик.
- **Bill reverse**: аввал receipt'ларга ФАОЛ landed cost тақсимоти
 йўқлиги текширилади (BR-BILL-012, Beruniy-005 - акс ҳолда юкланган
 қиймат/клиринг GL'да «осилиб» қоларди), кейин GL сторно
 (PostingService.reverseBySource) + ITEM сатрларининг inventory кирими
 `InventoryService.reverseReceive` билан АЙНАН ўз нархида қайтарилади
 (оддий issue ярамайди - FIFO'да бошқа партия ейилиб GL сторноси
 билан омбор қиймати ажралиб кетар эди). Ҳимоя (BR-BILL-010, inventory
 BR-INV-003/010'ни ўрайди): receipt шу (item, warehouse)даги ЭНГ
 ОХИРГИ ҳаракат бўлиши шарт (BR-INV-010, Beruniy-003 - AVCO ва FIFO
 учун бир хил; ўша bill'нинг ўз ҳаракатлари истисно) ва FIFO'да шу
 кирим партияси ТЎЛИҚ турган бўлиши шарт; AVCO'да қиймат асл unit
 cost'да айирилиб ўртача қайта ҳисобланади.
- **Payment post** (sourceModule=BILL_PAYMENT): AP Dt (тўлов
 валютасида, тўлов курсида) / банк счёти Cr. Аванс қисми ҳам шу
 ёзув ичида - AP дебети тўлиқ тўлов суммасига.
- **Realized курс фарқи - ҳар allocation учун алоҳида кичик JE**
 (sourceModule=PAYMENT_ALLOCATION, docId=allocation id): фарқ base =
 allocation × (bill курси - тўлов курси); мусбат (AP'да қарз base'и
 тўловдан катта) - фойда: AP Dt / EXCHANGE_GAIN_OR_LOSS Cr; манфий -
 тескари. Нол фарқ - JE ёзилмайди. Allocation тўлов билан бирга ҳам,
 кейин ҳам худди шу йўлдан ўтади (бир хиллик).
- **Payment reverse**: GL сторно + allocation'лар бекор (bill'ларнинг
 paid/status денормализацияси қайта ҳисобланади) + FX JE'лари ҳам
 сторно қилинади.

## Валидация (BR-BILL, BR-PAY)
| Код | Қоида |
|---|---|
| BR-BILL-001 | Vendor (VENDOR типдаги фаол контакт) шарт |
| BR-BILL-002 | Камида битта сатр |
| BR-BILL-003 | Сатр миқдори/суммаси мусбат |
| BR-BILL-004 | ITEM сатрида INVENTORY типдаги item ва омбор шарт |
| BR-BILL-005 | EXPENSE сатрида EXPENSE/COGS туркумидаги фаол postable счёт шарт |
| BR-BILL-006 | Vendor invoice рақами дубликати (409, partial unique DB'да ҳам) |
| BR-BILL-007 | Фақат DRAFT таҳрирланади/post қилинади |
| BR-BILL-008 | Фақат POSTED reverse қилинади |
| BR-BILL-009 | Чет валюта bill'ида мусбат курс шарт; home'да курс 1 |
| BR-BILL-010 | Reverse: киритилган товар ишлатилган ёки receipt'дан кейин омбор ҳаракати бор (BR-INV-003/010 ўрами) |
| BR-BILL-011 | Bill санаси шарт |
| BR-BILL-012 | Reverse тақиқ: receipt'ларга фаол landed cost тақсимоти бор - аввал ўша тақсимот reverse қилинади |
| BR-BILL-013 | Bill валютаси таъминотчи валютасига мос бўлиши шарт (Contact.currency, null = home; QBO қатъий, Arbitr-087) - server валютани контактдан ЎЗИ олади |
| BR-PAY-001 | Тўлов суммаси мусбат |
| BR-PAY-002 | Банк счёти BANK туридан, фаол ва postable |
| BR-PAY-003 | Allocation фақат POSTED bill'га |
| BR-PAY-004 | Allocation bill қолдиғидан (balance_due) ошмайди |
| BR-PAY-005 | Allocation'лар йиғиндиси тўлов суммасидан ошмайди |
| BR-PAY-006 | Тўлов валютаси bill валютаси билан бир хил (MVP) |
| BR-PAY-007 | Фақат POSTED тўлов reverse қилинади |
| BR-PAY-008 | Тўлов санаси шарт |
| BR-PAY-009 | Allocation ўша vendor'нинг bill'ига бўлиши шарт |
| BR-PAY-010 | Vendor (VENDOR типдаги фаол контакт) шарт |
| BR-PAY-011 | Бир тўловдан бир bill'га биттагина allocation (DB unique ҳам) |
| BR-PAY-012 | Чет валюта тўловида мусбат курс шарт; home'да курс 1 |
| BR-PAY-013 | Allocation фақат POSTED тўловдан (REVERSED тўлов тегилмас) |

## Landed cost (5-туртки)
Bill'даги LANDED_COST сатрлар INVENTORY_CLEARING'да тўпланади.
Тақсимот ҳужжати (LandedCostAllocation, LC-2026-NNNNN) клирингдаги
суммани танланган receipt'ларга тарқатади.

### Қарорлар (тасдиқланган)
- **Клирингдан эркин сумма**: тақсимот аниқ bill'га боғланмайди -
 фойдаланувчи суммани ўзи киритади (home валютада). Клиринг қолдиғи
 назорати қўлда (фойдаланувчи танлови; bill'га боғлаш рад этилди).
- **Қиймат бўйича автоматик нисбат**: сумма танланган receipt'ларнинг
 total_cost'ига пропорционал бўлинади (QBO Desktop default услуби);
 яхлитлашда қолдиқ охирги қаторга - жами айнан киритилган суммага
 тенг. Танланганлар қиймати жами нол бўлса BR-LC-007.
- **DRAFT йўқ**: яратилди = POSTED (тўлов модели), тузатиш reverse.
- Receipt = BILL манбали кирим ҳаракати (StockMovement IN,
 referenceType=BILL). Бошқа киримлар (adjustment, transfer) тақиқ
 (BR-LC-004). Бир ҳужжатда бир receipt биттагина (DB unique).

### Механика (ҳар receipt қатори учун; A - қатор улуши, Q - кирим
миқдори, R - ҳали ейилмаган қисм)
- Бирлик қўшимчаси delta = A / Q. Омборга кирадиган қисм
 (inventory_share) = R × delta, сотилган қисм (cogs_share) =
 A - inventory_share (аниқ комплемент, яхлитлаш дрейфисиз).
- FIFO: R = receipt яратган layer'нинг remaining_qty; layer unit_cost
 += delta, balance қайта ҳисобланади. AVCO: партия сақланмагани учун
 R «эски аввал сотилади» фарази билан баҳоланади: R = min(Q, жорий
 қолдиқ − шу receipt'дан КЕЙИН кирган миқдор), манфий бўлса 0
 (Beruniy-004: бутун қолдиқни олиш сотилган receipt харажатини
 кейинги партия активига ёзар эди); balance қиймати +=
 inventory_share, ўртача қайта.
- Миқдор ҳаракати ЙЎҚ (StockMovement ёзилмайди - бу қийматнинг қайта
 баҳоланиши); аудит из - тақсимот ҳужжатининг ўзи + GL.
- Қаторда тақсимот пайтидаги R (remaining_qty_at_alloc) сақланади -
 reverse guard'и учун.

### GL (posting-rules «Харид», sourceModule=LANDED_COST, docId=ҳужжат id)
Ҳар қатор: inventory_share > 0 - item asset счётига Dt
(warehouse/item dimension); cogs_share > 0 - SUPPLIES_MATERIALS_COGS
тизим счётига Dt (dimension'лар билан); жами INVENTORY_CLEARING Cr.

### Reverse
GL сторно (reverseBySource) + қийматлар айнан ортга: FIFO'да layer
remaining ТАҚСИМОТ ПАЙТИДАГИ билан тенг бўлиши шарт (акс ҳолда юкланган
қиймат қисман COGS'га кетиб бўлган - BR-LC-006), unit_cost -= delta;
AVCO'да қолдиқ ≥ remaining_qty_at_alloc ВА тақсимотдан кейин шу
(item, warehouse)да умуман ҳаракат бўлмаган бўлиши шарт (BR-INV-010
инварианти «қиймат ортга қайтариш фақат кейин ҳаракат бўлмаганда»,
Asrorxoja-001 - qty шартини оралиқ чиқим + янги кирим алдаб ўтар эди),
қиймат -= inventory_share.

Сторно санаси ҳужжатда сақланади (reversal_date, changeset 025) -
inventory valuation «санага» ҳисоботи улуш қайси кунгача кучда
бўлганини шундан билади (docs/modules/reports.md,
LandedValueContribution порти).

Bill reverse билан боғлиқлик (BR-BILL-012, Beruniy-005): receipt'ига
ФАОЛ (POSTED) тақсимот бўлган bill reverse қилинмайди - аввал тақсимот
reverse қилинади, акс ҳолда юкланган қиймат ва клиринг кредити GL'да
«осилиб» қолар эди. Кирим reverse'и (InventoryService.reverseReceive)
ҳам BR-INV-010 инвариантига бўйсунади: receipt шу (item, warehouse)даги
энг охирги ҳаракат бўлсагина қайтарилади (ўша bill'нинг ўз ҳаракатлари
истисно).

### BR-LC каталоги
| Код | Қоида |
|---|---|
| BR-LC-001 | Тақсимот суммаси мусбат |
| BR-LC-002 | Тақсимот санаси шарт |
| BR-LC-003 | Камида битта receipt танланиши шарт (такрор тақиқ) |
| BR-LC-004 | Receipt BILL манбали кирим бўлиши шарт |
| BR-LC-005 | Фақат POSTED тақсимот reverse қилинади |
| BR-LC-006 | Reverse тақиқ: тақсимотдан кейин омбор ҳолати ўзгарган |
| BR-LC-007 | Танланган receipt'лар қиймати нол - нисбат аниқланмайди |

### Экранлар
/landed-costs рўйхат, /landed-costs/new форма (сана, сумма, изоҳ,
охирги BILL киримлари checkbox билан), /landed-costs/{id} кўриш
(қаторлар inventory/COGS бўлиниши билан + reverse). Sidebar ХАРИД
бўлимига ҳавола.

## Туртки режаси
1. Spec + BR каталог + changeset 019 + domain entity'лар. ✅ (шу ҳужжат)
2. BillService: draft CRUD, post (GL + inventory), reverse (guard
 билан), vendor duplicate guard - тўлиқ тестлар.
3. BillPaymentService: post + allocation + FX-per-allocation + reverse,
 денормализация - тўлиқ тестлар.
4. UI: /bills рўйхат/форма/кўриш, тўлов формаси, AP aging экрани.
5. Landed cost тақсимоти (backend + тест + UI қисми).

## Тестлар (мажбурий рўйхат - 2-3-турткиларда)
- Bill post: debit == credit (home), inventory qty/қиймат ошади,
 AP кредит total_base'га тенг; ҳар сатр тури тўғри счётга.
- Чет валюта bill: base = amount × rate; home'да курс 1 (BR-BILL-009).
- Vendor invoice дубликати - BR-BILL-006 (DRAFT/POSTED'да), REVERSED
 дан кейин қайта киритиш ўтади.
- Reverse: GL сторно + inventory қайтади; товар сотилган бўлса
 BR-BILL-010.
- Payment: AP дебети тўлиқ суммага, банк кредити; allocation
 денормализацияси (UNPAID→PARTIAL→PAID); аванс unallocated'да.
- FX: bill 12 600 да, тўлов 12 700 да - allocation фарқи
 EXCHANGE_GAIN_OR_LOSS'га (иккала йўналиш), нол фарқда JE йўқ.
- Кейинги allocation (аванс ишлатиш) FX'ни тўғри ҳисоблайди.
- Payment reverse: allocation'лар бекор, bill status қайтади,
 FX JE'лар сторно.
- BR-PAY-003/004/005/006/009 guard'лари.

## Экранлар (4-туртки)
Sidebar ХАРИД бўлимига: Bill'лар (/bills), AP aging
(/reports/ap-aging). «+ Янги»: Bill, Тўлов. Ҳамма жадвал zebra +
.table-wrap, 375px, money формат.
