# Inventory (5-босқич) - SPEC

## Мақсад
Multi-warehouse омбор ҳисоби (Averpo'нинг QBO'дан атайлаб фарқи №1):
кирим/чиқим/инвентаризация/омборлараро кўчириш, AVCO ва FIFO
баҳолаш - иккаласи (item, warehouse) кесимида, метод CompanySettings'да
танланиб биринчи ҳаракатдан кейин қулфланади (BR-SET-003 порти).
Схема манбаи: docs/old-erp-ideas.md §6.

## Қатъий қарорлар (тасдиқланган)
- **Манфий қолдиқ ТАҚИҚ** (BR-INV-003): чиқим қолдиқдан ошса аниқ
 хато. FIFO'да манфий қолдиқ математик жиҳатдан бузуқ бўлар эди;
 DB'да ҳам CHECK (qty >= 0) инварианти туради.
- **Adjustment кўпайиш нархи**: формада unit cost ихтиёрий; бўш бўлса
 жорий қиймат олинади (AVCO - жорий ўртача, FIFO - охирги фаол layer
 нархи). Қолдиқ нол бўлса (жорий нарх аниқланмайди) нарх мажбурий -
 BR-INV-007.
- **Transfer иккита ҳаракат ёзуви** билан ифодаланади (TRANSFER_OUT
 манбада, TRANSFER_IN манзилда, бир-бирига counterpart омбор орқали
 боғланган) - эски лойиҳадаги бир-қаторли TRANSFER'дан фарқ: ҳар
 ёзув айнан битта омборга таъсир қилади, баланс ҳисоби бир хил
 йўлдан юради.
- Барча қийматлар (unit cost, жами) **home валютада** - валюта
 конверсияси ҳужжат (Bill/Invoice) қатламида бўлади.

## Entity'лар (changeset 017)

### Warehouse (warehouse)
| Майдон | Тип | Изоҳ |
|---|---|---|
| name | varchar(255), unique | BR-WH-001 |
| code | varchar(20), partial unique | Ихтиёрий қисқа код - BR-WH-002 |
| active | boolean | Нофаол омборга янги ҳаракат тақиқ (BR-INV-006) |

Seed: «Асосий омбор» (операциялар омборсиз юрмайди). Ўчириш йўқ -
фақат active=false (ҳаракатлар тарихи бузилмайди).

### StockMovement (stock_movement) - ўзгармас журнал
| Майдон | Тип | Изоҳ |
|---|---|---|
| type | enum | IN, OUT, ADJUST_IN, ADJUST_OUT, TRANSFER_IN, TRANSFER_OUT |
| item_id | UUID (DB FK item) | Dimension паттерни (JournalEntryLine каби) - модуллараро JPA боғланиш йўқ |
| warehouse_id | UUID FK | Таъсирланган омбор |
| counterpart_warehouse_id | UUID FK nullable | Фақат transfer: иккинчи томон |
| quantity | numeric(19,4) CHECK > 0 | Йўналиш type'дан |
| unit_cost | numeric(24,12) CHECK >= 0 | Home валютада |
| total_cost | numeric(19,4) CHECK >= 0 | Home валютада (чиқимда valuation натижаси) |
| movement_date | date | |
| reference_type / reference_id | varchar(30) / UUID | Полиморф ҳавола - 6-7-босқичда Bill/Invoice уланади |
| memo | varchar(500) | |

### StockBalance (stock_balance)
UNIQUE(warehouse_id, item_id); qty numeric(19,4) CHECK >= 0 (манфий
қолдиқ DB даражасида ҳам тақиқ), avg_cost numeric(24,12) - AVCO
ўртачаси (FIFO режимида ҳам маълумот учун юритилади).

### CostLayer (cost_layer) - FIFO партиялари
item_id, warehouse_id, received_date, unit_cost numeric(24,12),
original_qty / remaining_qty numeric(19,4) CHECK (remaining_qty >= 0
AND remaining_qty <= original_qty), is_exhausted, source_movement_id.
**Partial index (warehouse_id, item_id, received_date, id) WHERE NOT
is_exhausted** - «кейинги ейилмаган layer» қидируви тез.

### CostLayerConsumption (cost_layer_consumption)
layer_id FK, movement_id FK (OUT ҳаракат), quantity CHECK > 0 -
қайси партия қайси чиқимга ейилгани, тўлиқ audit из.

## Service API

### WarehouseService (1-туртки)
`all`, `active`, `get(id)`, `create(name, code)`,
`update(id, name, code, active)` - BR-WH-001/002.

### InventoryService (2-3-туртки, ягона public API)
- `receive(itemId, warehouseId, qty, unitCost, date, reference, memo)` -
 кирим: AVCO ўртача қайта ҳисобланади
 (янги ўртача = (эски qty × эски avg + qty × cost) / жами qty),
 FIFO'да янги layer.
- `IssueResult issue(itemId, warehouseId, qty, date, reference, memo)` -
 чиқим: AVCO'да qty × жорий avg; FIFO'да layer'лар received_date,
 кейин id тартибида ейилади (consumption ёзувлари билан). Қайтарган
 қиймати (home) 6-7-босқичда COGS проводкасига киради.
- `adjust(itemId, warehouseId, deltaQty, unitCostOrNull, date, memo)` -
 инвентаризация: фарқ ADJUST_IN/ADJUST_OUT + GL проводка
 PostingService орқали (posting-rules «Омбор»: камайиш
 OTHER_COSTS_OF_SERVICE_COS Dt / INVENTORY Cr, кўпайиш тескари;
 sourceModule=INVENTORY, docId=movement id - BR-LED-012 idempotency
 ва BR-LED-020 период қулфи автоматик).
- `transfer(itemId, fromWarehouseId, toWarehouseId, qty, date, memo)` -
 GL проводка ЙЎҚ (posting-rules): AVCO'да қиймат манба ўртачасида
 манзилга кўчади; FIFO'да ейилган layer'лар манзилда худди шу
 unit_cost/received_date билан қайта яратилади.
- `InventoryValuationLock` импли: ҳаракат мавжуд бўлса метод қулф.
- Item текшируви ItemService (public API) орқали: фақат INVENTORY тип.

## Валидация (BR-WH, BR-INV)
| Код | Қоида |
|---|---|
| BR-WH-001 | Омбор номи бўш эмас ва unique |
| BR-WH-002 | Омбор коди (киритилса) unique |
| BR-INV-001 | Ҳаракат фақат INVENTORY типдаги item учун |
| BR-INV-002 | Миқдор мусбат |
| BR-INV-003 | Омборда етарли қолдиқ йўқ (манфий қолдиқ тақиқ) |
| BR-INV-004 | Unit cost манфий бўлмаган сон |
| BR-INV-005 | Transfer'да манба ва манзил омбор ҳар хил |
| BR-INV-006 | Нофаол омборга янги ҳаракат тақиқ |
| BR-INV-007 | Қолдиқ нолда кўпайиш adjustment'ига нарх шарт |
| BR-INV-008 | Ҳаракат санаси шарт |
| BR-INV-009 | Чиқимни қайтариб бўлмайди: ейилган партия нархи кейин ўзгарган (landed cost) - қиймат GL сторноси билан мос келмайди |
| BR-INV-010 | Қийматни ортга қайтариш (кирим reverse, landed cost reverse) фақат шу (item, warehouse)да КЕЙИН бошқа ҳужжат ҳаракати бўлмаганда - акс ҳолда AVCO/FIFO таннарх тарихи бузилади; манба ҳужжатнинг ўз ҳаракатлари истисно, тузатиш adjustment орқали |
| BR-INV-011 | Ҳужжатли актда (StockAdjustment/StockTransfer, Arbitr-093) камида битта сатр бўлиши шарт |
| BR-INV-012 | Ҳужжатли акт сатрларида битта item такрорланмайди (UNIQUE(акт_id, item_id); Arbitr-093) |

## Posting
posting-rules.md «Омбор» жадвали: Adjustment GL'га боради, Transfer
GL'сиз. Receive/Issue GL проводкаси ҳужжат модулларида (Bill/Invoice,
6-7-босқич) - inventory фақат миқдор/қиймат ҳисобини беради.

## Тестлар (мажбурий рўйхат)
1-туртки: Warehouse CRUD - ном/код unique, нофаол қилиш.
2-туртки: AVCO - кирим ўртачани тўғри ҳисоблайди (аралаш нархлар),
чиқим qty × avg; FIFO - layer тартиби, қисман ейилиш, consumption
изи, is_exhausted; манфий қолдиқ - BR-INV-003; нотўғри item типи -
BR-INV-001; қулф - биринчи ҳаракатдан кейин BR-SET-003.
3-туртки: adjustment GL проводкаси posting-rules'га мос (debit ==
credit), нол қолдиқда нарх шарт (BR-INV-007), transfer иккала омбор
балансини тўғри ўзгартиради ва GL'га тегмайди, FIFO transfer layer
нархларини сақлайди.

## Экранлар (4-туртки)
Sidebar'да янги «ОМБОР» бўлими: Қолдиқлар (/inventory/balances,
омбор филтри), Ҳаракатлар (/inventory/movements), Инвентаризация
(/inventory/adjustments/new), Кўчириш (/inventory/transfers/new);
Созламалар каталогларида /settings/warehouses. Ҳамма жадвал zebra +
.table-wrap, 375px.

## ҲУЖЖАТЛИ Adjustment/Transfer + филтрлар (Arbitr-093, фойдаланувчи қарори)

Фойдаланувчи талаби: бир-амаллик формалар ўрнига КЎП САТРЛИ ҳужжатлар
(QBO Inventory Qty Adjustment ҳам кўп сатрли ҳужжат) + рўйхат/view +
Қолдиқлар ва Ҳаракатларда мукаммал филтрлар.

### StockAdjustment (stock_adjustment + stock_adjustment_line, changeset 056)
- Сарлавҳа: рақам **ADJ-YYYY-NNNNN** (префикс grep билан текширилган -
 эркин), сана, БИТТА омбор (инвентаризация акти омбор бўйича), изоҳ,
 ташқи ҳужжат № `external_ref` (қоғоз/ташқи Reference no., ихтиёрий
 nullable, max 50 белги - Arbitr-109, changeset 060). Фарқи очиқ:
 ички ADJ- рақамини тизим ўзи беради, external_ref ташқи/қоғоз
 ҳужжат рақами учун.
- Сатр: item, жорий qty ва бирлик ҳинти (кўрсатилади - Arbitr-109),
 ЯНГИ qty киритилади → delta авто (QBO «New quantity» услуби),
 unit cost ихтиёрий (BR-INV-007 қоидаси сатрга).
 UNIQUE(adjustment_id, item_id) - BR-INV-012; актда камида битта
 сатр - BR-INV-011.
- Сақлаш = дарҳол POSTED (SalesReceipt нақши, draft йўқ): ҳар сатр
 StockMovement (ADJUST_IN/OUT, reference_type=STOCK_ADJUSTMENT,
 reference_id=акт id) + БИТТА JE (posting-rules «Омбор» янги банди).
- Reverse: қарши-акт prefill (ҳар сатр тескари delta) - POSTED
 ўзгармас қоидаси сақланади; тарихий movements'га тегилмайди.
- Рўйхат /inventory/adjustments (Page<> + pagination билан ТУҒИЛАДИ),
 филтрлар: омбор, сана оралиғи. View: сарлавҳада ташқи ҳужжат №
 (бор бўлса), сатрлар + GL линк (080 нақши).

### StockTransfer (stock_transfer + stock_transfer_line, changeset 056)
- Сарлавҳа: рақам **WTR-YYYY-NNNNN** (эркинлиги текширилган), сана,
 манба/манзил омбор (BR-INV-005), изоҳ, ташқи ҳужжат №
 `external_ref` (қоғоз/ташқи Reference no., ихтиёрий nullable,
 max 50 белги - Arbitr-109, changeset 060; ички WTR- рақамидан
 фарқли - уни тизим беради). Сатр: item, qty - манба омбор қолдиғи
 ва бирлик ҳинти кўрсатилади (Arbitr-109).
- Сақлаш = POSTED: ҳар сатр TRANSFER_OUT+TRANSFER_IN жуфти
 (reference акт id), GL ЙЎҚ (аввалгидек). Reverse: қарши-акт prefill.
 Актда камида битта сатр - BR-INV-011; сатрда item такрори тақиқ -
 BR-INV-012.
- Рўйхат /inventory/transfers + view (сарлавҳада ташқи ҳужжат №,
 сатрлар, GL линк ЙЎҚ).

### Эски оқим тақдири
- Бир-амаллик формалар ЎРНИНИ янги кўп сатрли формалар олади
 (маршрутлар сақланади, эски энди list'га олиб боради; /new - янги
 форма). InventoryService.adjust/transfer per-movement методлари
 ҚОЛАДИ (ички ва тарихий); янги adjustDocument/transferDocument
 методлари қўшилади (JE битта - акт даражасида).
- Эски (ҳужжатсиз) movements тарихий - рўйхат/view'ларга кирмайди,
 Ҳаракатлар журналида кўринаверади («ҳужжатсиз» деб).

### Филтрлар (мукаммал тўплам - фойдаланувчи талаби)
- **Қолдиқлар (/inventory/balances)**: item ном қидируви, омбор,
 категория, «нолни яшир» (default: яширилган).
- **Ҳаракатлар (/inventory/movements)**: ТУР (кирим/чиқим/тузатиш/
 кўчириш), омбор, item қидируви, сана оралиғи, ҳужжат рақами.
 068 ListFilter нақши; ҳар икки рўйхат филтрлари pagination билан
 ҳамкор (query параметрлар сақланади).

### Тестлар (қўшимча рўйхат)
Акт post: сатрлар movements'га тўғри (reference акт id), JE битта ва
debit==credit (аралаш акт: кўпайиш+камайиш бир актда), нол қийматли
сатр легсиз; янги qty → delta ҳисоби; BR-INV-003/007 сатр даражасида;
transfer акт иккала омбор балансини тўғри ўзгартиради, GL'сиз
(journal_entry сони ўзгармайди); reverse қарши-акт тўғри prefill;
филтрлар (тур/омбор/сана) тўғри кесим; эски бир-амаллик тарихий
ёзувлар рўйхатларга кирмайди.
