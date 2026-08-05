# Class tracking (Йўналиш кесими) - SPEC

## Мақсад

QBO Class паритети (Arbitr-015): даромад/харажатни бизнес йўналишлари
(бўлим, филиал, фаолият тури) кесимида кузатиш. Сатр даражасидаги
таҳлилий тег - GL суммаларига МУТЛАҚО таъсир қилмайди, фақат P&L'ни
сегментлайди («P&L by Class» ҳисоботи).

Эталон: Finance.xsd `Class` (8849: Name 100 белги / SubClass /
ParentRef / FullyQualifiedName «Ота:Бола» / Active), `ClassRef` сатр
даражасида (4975, 6194, 7392 ва б.), Preferences
`ClassTrackingPerTxn` / `ClassTrackingPerTxnLine` (11869/11878).
Location/Department АТАЙЛАБ ЙЎҚ - фойдаланувчи қарори:
фақат Class етади. Location учун спец бор (location-tracking.md),
тасдиқ кутмоқда.

## Дизайн қарорлари

- **Жойлашув - shared модули** (Currency каталоги нақши): ledger
 posting'да class фаоллигини текшириши керак, ledger эса фақат
 shared'га боғлана олади (қоида №6). Шунинг учун каталог
 `shared.domain`/`shared.service` да туради.
- **Java номи `TxnClass`, жадвал `txn_class`**: `Class` - Java reserved
 сўзи, `class` package номи ҳам бўла олмайди. UI label i18n орқали:
 уз «Йўналиш», ru «Класс», en «Class».
- **Class ҳар доим САТРДА сақланади** (жадвал даражасида ҳам, GL'да
 ҳам). PER_TXN режим фақат UI қулайлиги: формада битта select,
 controller қийматни ҳамма сатрга тарқатади - схема ягона қолади,
 QBO API хулқига мос (у ҳам line'га ёзади).
- **Режим UI'ни бошқаради, service'ни эмас**: service келган classId'ни
 режимдан қатъи назар қабул қилади (BR-CLS-001 текшируви билан) -
 режим алмашганда эски ҳужжатлар ва тестлар бузилмайди.

## Entity'лар

`txn_class` (changeset 037), BaseEntity майдонларига қўшимча:

| майдон | тип | изоҳ |
|---|---|---|
| name | varchar(100) NOT NULL | QBO Name чегараси |
| parent_id | uuid NULL, FK txn_class | sub-class (QBO ParentRef); чуқурлик чекланмаган - счёт дарахти нақши |
| active | boolean NOT NULL default true | ўчириш ЙЎҚ - фақат деактив (QBO услуби; GL тарихида ишлатилган бўлиши мумкин) |

- UNIQUE NULLS NOT DISTINCT (parent_id, name) - бир ота ичида ном
 ноёб, top-level'da ҳам (PostgreSQL 18 қўллайди).
- Кўрсатишда FullyQualifiedName услуби: «Ота:Бола» (сақланмайди,
 ҳисобланади - QBO'да ҳам output-only).

`company_settings`: `track_classes varchar(10) NOT NULL DEFAULT 'OFF'` -
enum `ClassTrackingMode { OFF, PER_TXN, PER_LINE }`. Қулфланмайди -
исталган пайт алмаштирилади, эски ҳужжатлар ўзгармайди (QBO ҳам шундай).

Сатр жадвалларига `class_id uuid NULL FK txn_class`:
`journal_entry_line` (+ индекс idx_jel_class - ҳисобот шу устундан
ўқийди), `invoice_line`, `bill_line`, `bank_transaction_line`.
Backfill ЙЎҚ - эски сатрлар class'сиз қолади, ҳисоботда
«Кўрсатилмаган» устунига тушади (QBO'да ҳам шундай).

Class ОЛМАЙДИГАН ҳужжатлар: Transfer, ReceivePayment/BillPayment,
opening balance. ДАЛИЛ ТУЗАТИЛДИ (Otabek-008): Transfer'да
Finance.xsd'да ClassRef аслида БОР (:10316) - бу ОНГЛИ ФАРҚ: бизда
class кўлами фақат P&L (P&L by Class); transfer соф BS ҳужжати -
QBO у ерда class'ни Balance Sheet by Class учун ишлатади, биз бу
ҳисоботни қурмаймиз. Зарурат чиқса - алоҳида туртки.
Inventory adjustment / landed cost - 2-босқич.

## Service API

- `shared.service.TxnClassService`: `all`, `activeForSelect`
 (дарахт тартибида, «Ота:Бола» номи билан), `get(id)`,
 `create(name, parentId)`, `rename(id, name)`, `activate/deactivate(id)`.
 Delete API ЙЎҚ.
- `ClassTrackingMode` - CompanySettingsService'дан ўқилади.
- `PostingService`: `JournalEntryRequest.Line`'га `classId` (nullable)
 қўшилади; post пайтида null бўлмаса BR-CLS-001 (фаоллик)
 текширилади (TxnClassService орқали - shared, қоида №6 сақланади).

## Posting

GL суммаларига таъсир ЙЎҚ - соф tagging. posting-rules.md'га «Class
кўчиши» изоҳ банди қўшилади (қоида №8):

- ҳужжат сатридаги class posting'да ўша сатрнинг GL сатрига айнан
 кўчади;
- назорат/жами сатрлар (AR, AP, банк жами) class ОЛМАЙДИ - уларда бир
 нечта class аралашади, QBO ҳам control томонни тегсиз қолдиради;
- техник сатрлар (EXCHANGE_GAIN_OR_LOSS, penny rounding) class'сиз.

P&L by Class фақат INCOME/EXPENSE классификациясини кесади - назорат
сатрлари барибир Balance Sheet'да, ҳисоботга кирмайди.

## Валидация ва инвариантлар

Янги BR кодлари (аввал docs/business-rules.md каталогига - қоида №13):

- **BR-CLS-001**: ҳужжатда танланган Class фаол бўлиши шарт.
- **BR-CLS-002**: Class номи бир ота ичида ноёб (DB unique + тушунарли
 хато).
- **BR-CLS-003**: ота сифатида ўзи ёки ўз авлоди танланмайди (цикл
 тақиқ - счёт дарахти нақши).

Инвариант: class GL суммасини ўзгартирмайди - posting тестларида
debit == credit тенгламаси class билан/class'сиз бир хил.

## Тестлар (мажбурий рўйхат)

1. Invoice/Bill posting: сатр class'и тегишли GL сатрига кўчади;
 AR/AP назорат сатри class'сиз.
2. Чет валюта ҳужжатида FX/rounding сатрлари class'сиз.
3. BR-CLS-001: деактив class'ли сатр рад.
4. BR-CLS-002/003: каталог қоидалари (дубликат ном, цикл).
5. P&L by Class: устунлар йиғиндиси (Кўрсатилмаган билан) айнан оддий
 P&L жамига тенг - ҳар сатрда ва Net Income'да.
6. PER_TXN режими: формадаги битта танлов ҳамма сатрга тарқалади
 (controller тести).
7. OFF режимда формада class майдони чиқмайди (smoke/HTML текшируви).
8. ScreenSmokeTest: /settings/classes,
 /reports/profit-and-loss-by-class.

## Экранлар (JTE routes)

- `/settings/classes` - каталог: дарахт кўриниш («Ота:Бола»), яратиш,
 номлаш, актив/деактив (мавжуд /settings/* ҳуқуқ нақши билан бир
 хил). Мобил 375px.
- Ҳужжат формалари (invoice, bill, deposit/expense, қўлда JE):
 - `PER_LINE` - ҳар сатрда select (activeForSelect, «Ота:Бола»);
 - `PER_TXN` - сарлавҳада битта select, сатрларга тарқатилади;
 - `OFF` - class майдонлари умуман кўринмайди (default).
- `/reports/profit-and-loss-by-class` - давр филтри; устунлар: даврда
 ишлатилган ҳар class (тўлиқ ном, sub-class алоҳида устун - rollup
 ЙЎҚ, QBO услуби) + «Кўрсатилмаган» + Жами; сатрлар оддий P&L
 тузилмаси. Сарлавҳада «Барча суммалар <home> да». .table-wrap
 (устун кўп бўлиши мумкин).
- Sidebar: ҳисобот Ҳисоботлар гуруҳига, каталог Созламаларга.

## 2-босқич (ҳозир ЭМАС - алоҳида келишилади)

- «Warn when no class» огоҳлантириши (QBO preference).
- Inventory adjustment / landed cost сатрларига class.
- Balance Sheet by Class - ҚИЛИНМАЙДИ (QBO'да ҳам чекланган ва
 чалкаш; BS назорат сатрлари class олмайди).
- Budget by Class (Finance.xsd 13387) - бюджет модули пайдо бўлса.
