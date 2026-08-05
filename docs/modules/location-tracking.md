# Location tracking (Жойлашув кесими) - SPEC

Ҳолат: **ТАСДИҚ КУТМОҚДА** (фойдаланувчи талаби: QBO'дан
Class олинган, Location ҳам қўшилсин; спец Отабек (QBO) + Наргиза
кўригидан кейин карта).

Манба: QBO parity бўшлиғи - entities.md JE жадвалидаги DepartmentRef
(Location) қатори кейинчалик кечиктирилган эди; энди зарурат
чиқди. Class нақшини такрорлайди (docs/modules/class-tracking.md),
лекин QBO Location'нинг ФАРҚЛИ хусусиятлари билан.

## QBO билан солиштирув

Барчаси **docs/qbo-reference/Finance.xsd дан ТАСДИҚЛАНГАН** (веб эмас,
engineering-rules.md қоидаси - манба ҳақиқати):
- **Location = Department** (Finance.xsd `Department` complexType,
  :9210): «track different segments of the business, break down
  income/expenses for each segment. Department can apply to ALL
  transactions». **Ном чалкашлиги ЙЎҚ**: QBO API'да «Department»,
  UI'да «Location» - битта нарса (фойдаланувчи «адашгандирман» деди -
  адашмаган, иккови QBO'нинг ўзида бир хил тушунча).
- **Иерархик** (тасдиқ): `SubDepartment` (boolean) + `ParentRef`
  (Finance.xsd:9230) - TxnClass каби parent/sub.
- **Жойлашуви (Finance.xsd тасдиғи - QBO АЙНАН)**:
  - Кўп ҳужжатда HEADER: `DepartmentRef` (:4614) = «**Location of the
    transaction**» (TxnDate ёнида, битта ҳужжатга битта).
  - **Journal Entry'да САТР**: `DepartmentRef` (:7619)
    `JournalEntryLineDetail` ичида = «associated with the JournalEntry
    line» - ClassRef каби сатр даражали. Бизда class_id аллақачон JE
    сатрида → location ҳам АЙНАН ёнида (қўшимча иш эмас).
- **Class'дан фарқ**: Location'да PER_LINE/PER_TXN режими ЙЎҚ (header
  ҳужжатларда битта, JE'да сатрда - QBO шундай).
- «P&L by Location» ҳисоботи (P&L by Class айнан нақши).
- **Ёрлиқ қайта номланиши** (QBO UI хусусияти - Finance.xsd'да ЙЎҚ,
  QBO билимдан; фойдаланувчи кейинчалик рўйхатни ШУ ҲОЛДА ҚАБУЛ
  қилди): «Location» ни Business/Department/Division/Location/
  Property/Store/Territory дан танлайди - экранда шу ном.

## Дизайн қарорлари

- **Location** = янги иерархик каталог (TxnClass нақши: parent,
  fullyQualifiedName «:» билан, active). **ФАРҚ (QBO айнан)**:
  иерархия **макс 5 даража** (QBO Class/Location чегараси) - TxnClass
  чексиз, лекин Location'да BR-LOC билан 5'га чекланади (фойдаланувчи
  қарори: QBO солиштируви, бир хил).
- **Ёрлиқ**: CompanySettings'га `location_label` (enum:
  LOCATION/DEPARTMENT/DIVISION/STORE/PROPERTY/TERRITORY/BUSINESS,
  default LOCATION) - экранларда/ҳисоботларда шу ном (uz/ru/en
  messages). QBO «renameable label».
- **Режим**: CompanySettings'га `track_locations` (enum OFF/ON -
  Class'даги PER_TXN/PER_LINE режими Location'да ЙЎҚ). Default OFF.
  Ёқилса ҳужжат формаларида location танлагич чиқади.
- **location_id жойлашуви (QBO АЙНАН - Finance.xsd)**:
  - HEADER (битта location): invoice, bill, expense, sales_receipt,
    credit_memo, vendor_credit, refund_receipt, estimate,
    purchase_order, bank_transaction, payroll_run. Nullable.
  - **JE'да САТР даражали**: `journal_entry_line.location_id` -
    QBO `JournalEntryLineDetail.DepartmentRef` айнан шундай
    (Finance.xsd:7619); мавжуд `class_id` JE сатрида - location ҳам
    ёнида, айнан ўша нақш (қўшимча плумбинг эмас).
- **Warehouse ≠ Location** (entities.md): warehouse - физик омбор
  (inventory), location - бухгалтерия кесими. Иккови алоҳида.
- **Class'дан мустақил**: битта ҳужжат ҳам Class (сатр), ҳам Location
  (header) га эга бўла олади - QBO'да ҳам шундай.

## Entity ва changeset (064)

- **location** жадвали: id, name, code (ихтиёрий), parent_id
  (self-ref nullable), active. (TxnClass DDL нақши.)
- **company_settings**'га: `track_locations` (OFF/ON, default OFF),
  `location_label` (enum, default LOCATION).
- Ҳужжат HEADER жадвалларига `location_id` (FK location, nullable):
  invoice/bill/expense/sales_receipt/credit_memo/vendor_credit/
  refund_receipt/estimate/purchase_order/bank_transaction/payroll_run.
- **journal_entry_line'га `location_id`** (сатр даражали - QBO JE
  айнан шундай; мавжуд class_id ёнида).
- **Changeset 064 БАНД** (реестр: NAVBAT).

## Service ва posting

- `LocationService` (TxnClassService нақши): CRUD, дарахт, active,
  fullyQualifiedName. Каталог экрани.
- Posting: location_id GL'га ТАЪСИР ҚИЛМАЙДИ - у фақат кесим тег
  (Class каби; JournalEntry'га sourcedan кўчади ёки header'да
  сақланади). PostingService мантиғи ЎЗГАРМАЙДИ - фақат тег кўчади.
- Ҳисобот: `ProfitAndLossByLocationService` +Controller
  (ProfitAndLossByClass айнан нақши) - P&L location бўйича
  гуруҳланади/филтрланади.

## Экранлар (JTE)

- Каталог: `/locations` (CRUD, дарахт - TxnClass экрани нақши),
  ёрлиқ CompanySettings'дан. helpKey.
- Settings: `track_locations` toggle + `location_label` select
  (SUPER_ADMIN).
- Ҳужжат формалари: track ON бўлса header'да `locationSelect`
  компоненти (classSelect нақши), ёрлиқ билан.
- Ҳисобот: `/reports/profit-loss-by-location` (P&L by Class нақши).

## Валидация ва инвариантлар

- track_locations OFF бўлса location майдони кўринмайди, эски
  ёзувлар сақланади; ON→OFF қайтса маълумот ўчмайди (яширинади).
- Иерархияда цикл тақиқи (parent ўзи/авлодига эмас - TxnClass
  инварианти).
- **Иерархия макс 5 даража** (QBO айнан) - BR-LOC-001 (таклиф -
  каталогга спец тасдиқлангач киради): 5-даражадаги location'га sub
  қўшиб бўлмайди (parent занжири текширилади).
- Location home валютага/GL балансига таъсир қилмайди (соф тег).

## Class билан муносабат ва org-structure

- **Class ↔ Location**: иккови мустақил QBO ўлчови - бир ҳужжатда
  бирга бўла олади. Такрор эмас.
- **org-structure (docs/modules/org-structure.md) билан**: Location =
  QBO-стандарт ЯГОНА транзаксия ўлчови (тег+ҳисобот); org-structure =
  мослашувчан кўп-структурали org-КЎРУВ (ҳозир тегсиз). Ҳозир алоҳида.
  2-БОСҚИЧ ОГОҲЛАНТИРИШИ: org-structure тег-босқичи келганда, Location
  ва org-structure тегларини БИРЛАШТИРИШ ёки алоҳида қолдириш
  келишилади (такрор тег-механизм қурмаслик учун) - арбитр эслатмаси.

## Тестлар (минимум)

Location каталог CRUD + дарахт + цикл тақиқи; **5-даража чегараси**
(6-чи sub → BR-LOC-001 рад; код таклиф - каталогга спец тасдиқлангач
киради); track OFF/ON render (майдон кўриниши);
location_label ўзгарса экран номи (uz/ru/en); ҳужжат сақлашда
location_id header'да + **JE'да сатрда**; P&L by Location гуруҳлаш;
Class билан бирга бир ҳужжатда; мавжуд Class/posting тестлари яшил.
Changeset dev boot ddl validate.

## 2-босқич (ҳозир ЭМАС)

Location бўйича бошқа ҳисоботлар (Balance Sheet by Location, Sales by
Location - QBO Plus); org-structure билан тег бирлаштируви (юқорида).
