# Transfer - счётлараро пул ўтказмаси (QBO Transfer) - SPEC

## Мақсад
QBO «+ Янги → Ўтказма» (Transfer) паритети: иккита Balance Sheet счёти
орасида пул кўчириш - алоҳида, битта мақсадли экран. Ҳозир транзфер
умумий Bank Transactions формасида (тур=TRANSFER) кўмилган ва фақат
BANK счётларига чекланган; бу QBO'нинг тоза Transfer формасига мос
эмас (Arbitr-022, фойдаланувчи скриншот солиштируви, 2026-07-07).

## QBO билан солиштирув
Finance.xsd:10286 (Transfer): FromAccountRef/ToAccountRef «Must be a
Balance Sheet account», битта Amount, ихтиёрий ClassRef, Transaction'дан
TxnDate/PrivateNote. UI (скриншот): «Transfer Funds From» (+ ёнида
Balance), «Transfer Funds To» (+ Balance), Currency, Transfer Amount,
Date, Memo, Attachments, «Make recurring», Save/Cancel.

Balance Sheet счёти = classification ∈ {ASSET, LIABILITY, EQUITY}.
Income/Expense транзферга кирмайди (улар deposit/expense оқими).

## Қатъий қарорлар (2026-07-07 фойдаланувчи билан келишилган)

- **КЎЛАМ**: транзфер ҳар қандай Balance Sheet счёти орасида - фаол,
  postable, ASSET/LIABILITY/EQUITY. Bank, касса (CASH_ON_HAND), заём,
  аванс, капитал счётлари ҳаммаси мумкин. Credit Card тури бизда йўқ
  (Otabek-001 рад) - автоматик четда.
- **ИСТИСНО - тизим назорат счётлари** (Komil-008, BR-TXF-002): қуйидаги
  detail type'лар манба ҳам, манзил ҳам бўлолмайди ва dropdown'да
  кўринмайди - `AccountDetailType.systemManaged()`:
  `ACCOUNTS_RECEIVABLE`, `ACCOUNTS_PAYABLE`, `INVENTORY`,
  `INVENTORY_CLEARING`, `UNDEPOSITED_FUNDS`, `PAYROLL_CLEARING`
  (23а payroll'да қўшилган - Ulugbek-019 янгилови),
  `OPENING_BALANCE_EQUITY`, `RETAINED_EARNINGS`. Сабаб: бу счётлар
  фақат ўз subledger хизмати
  (Invoice/Bill, inventory, landed cost, deposit оқими, йил ёпилиши)
  орқали ёзилади; қўлда ўтказма GL қолдиғини ўзгартиради, subledger'ни
  эмас - AR/AP aging ва inventory valuation GL билан ажралиб қолади
  (IAS 2 / IAS 1 мувофиқлик). Реал QBO Transfer UI ҳам тизим счётларини
  кўрсатмайди - бу QBO'га яқинлашиш. `SALES_TAX_PAYABLE` рўйхатга
  атайлаб КИРМАЙДИ: алоҳида tax payment оқими йўқ, ҚҚС тўлови ҳозирча
  айнан transfer/expense орқали қилинади - оқим қурилганда қайта
  кўрилади.
- **АЛОҲИДА ЭКРАН**: dedicated /transfers (рўйхат + форма), «+ Янги»да
  «Ўтказма». Умумий bank-txn формасидан TRANSFER тури ОЛИБ ТАШЛАНАДИ -
  икки жойда такрор форма қолмасин.
- **MULTI-CURRENCY САҚЛАНАДИ** (QBO'дан устунлигимиз): бир хил валютада
  битта «Ўтказма суммаси» (QBO кўриниши); From/To валютаси фарқ қилса
  FX сатри (кросс-курс + манзил сумма) очилади - progressive disclosure.
- **CLASS ҚЎШИЛМАЙДИ** (онгли фарқ - Otabek-008, зарурат чиқса).
- **RECURRING ва ATTACHMENTS йўқ** (recurring - алоҳида кейинги иш;
  attachments - Arbitr-013 backlog).

## Модел
Схема ЎЗГАРМАЙДИ - миграция йўқ. Мавжуд `bank_transaction` жадвали +
`BankTransactionType.TRANSFER` қайта ишлатилади (from/to счёт id,
from/to сумма+курс майдонлари аллақачон бор - Arbitr-012). «bank»
номи фақат ички қолади; UI доим «Ўтказма» кўрсатади. Кейин зарурат
чиқса алоҳида entity'га ажратиш - ҳозир MVP учун онгли соддалаштириш.

## Проводка (posting-rules.md - «Банк» бўлими умумлаштирилади)
Транзфер проводкаси мавжуд «Валюта конверсия» сатридан келиб чиқади,
фақат «банк счёти» → «Balance Sheet счёти» деб кенгайтирилади:

| Ҳужжат | Дебет | Кредит |
|---|---|---|
| Ўтказма (бир хил валюта) | манзил BS счёти | манба BS счёти |
| Ўтказма (валюта фарқли) | манзил BS счёти | манба BS счёти, base фарқи EXCHANGE_GAIN_OR_LOSS |

- Пул манбадан чиқади (Cr) → манзилга киради (Dr). LIABILITY/EQUITY
  томони ҳам GL'да тўғри балансланади (масалан заёмни ёпиш: Dr заём /
  Cr банк - ҳақиқий бухгалтерия, QBO ҳам шунга рухсат беради).
- FX: base фарқи (олинган base - берилган base) EXCHANGE_GAIN_OR_LOSS'га
  (мавжуд BankTransactionService.transfer мантиғи - ўзгармайди).
- Проводка фақат PostingService орқали (қоида №2), home'да балансланади
  (қоида №4), debit == credit assert (қоида №7).

## Бизнес қоидалар (BR-TXF-* - каталог-аввал, қоида №13)
Код ёзишдан ОЛДИН docs/business-rules.md га киритилади (Ғайрат Tax'нинг
BR-TAX'лари commit бўлгач - business-rules.md тўқнашмасин):

- **BR-TXF-001** (ЯГОНА янги код): манба ва манзил счёти Balance Sheet
  (ASSET/LIABILITY/EQUITY), фаол ва postable бўлиши шарт.
- **BR-TXF-002** (Komil-008, 2026-07-07): манба ва манзил счёти
  тизим-бошқарув назорат счёти (`systemManaged()`) бўлмаслиги шарт -
  юқоридаги «ИСТИСНО» банди.
- Қолгани МАВЖУД BR-BT кодларини қайта ишлатади (янги код керак эмас):
  ҳар хил счёт - BR-BT-005 (матни «банк»дан «счёт»га умумлаштирилди);
  мусбат сумма - BR-BT-001; сана - BR-BT-006; курс - BR-BT-008.

Мавжуд BR-BT-002 (BANK шарт) транзферга ТЕГМАЙДИ - у deposit/expense
оқимида қолади.

## UI (dedicated /transfers)
- Форма: «Манбадан» (tree-select, ёнида жонли Balance), «Манзилга»
  (tree-select + Balance), «Ўтказма суммаси», «Сана», «Изоҳ». Валюта
  манба счётидан; From/To фарқ қилса FX сатри (кросс-курс + манзил
  сумма авто, қўлда устун) - Arbitr-012 UI'си қайта ишлатилади.
- Tree-select: Arbitr-014 shared/accountOptions - манба ҳар қандай BS
  счёти (disableNonPostable=true, гуруҳ счётлар disabled).
- Рўйхат: /transfers - сана, манба→манзил, сумма, ⋮ (Кўриш/сторно).
  shared/rowMenu (Nargiza-007 P1a).
- Sidebar «+ Янги»да «Ўтказма»; БАНК бўлимида «Ўтказмалар» линки.
- Mobile-first: 375px, .table-wrap.

## Қамровдан ташқари (ҳозир қилинмайди)
- Make recurring (такрорий ҳужжат шаблонлари) - алоҳида кейинги иш.
- Attachments (Arbitr-013 backlog).
- Class dimension (Arbitr-015 backlog).
- Credit Card тури (бизда йўқ - Otabek-001 рад).

## Ижро тартиби (арбитр ўзи кодлайди, Tax тугагач)
1. ✅ (1-бўлак, 2026-07-07): business-rules.md га BR-TXF-001 + enum'га
   BR_TXF_001; AccountClassification.isBalanceSheet(); BankTransactionService
   .transfer() энди requireTransferAccount (Balance Sheet гарови)
   ишлатади; BR-BT-005 матни «банк»→«счёт» умумлаштирилди.
2. posting-rules.md «Банк» бўлимини умумлаштириш (Ўтказма сатрлари).
3. requireBalanceSheetAccount гарови (AccountClassification.isBalanceSheet
   helper ёки Account.isBalanceSheet); transfer() шуни ишлатади.
4. Dedicated TransferController (/transfers рўйхат+форма) + JTE экранлар;
   умумий bank-txn формасидан TRANSFER тури олиб ташланади.
5. Sidebar «+ Янги» ва БАНК линклари.
6. Тестлар: транзфер post (debit == credit), FX фарқи EXCHANGE_GAIN_OR_LOSS,
   BS-эмас счёт рад (BR-TXF-001), бир хил счёт рад (BR-BT-005), сторно.
7. ./gradlew test тўлиқ яшил, 375px кўз текшируви.

БАЖАРИЛДИ (1-7 банд, Arbitr-022 done'да, 2026-07-07). Қуйида давоми.

## Такомиллаштиришлар (фойдаланувчи билан келишилган, 2026-07-07)

Дедикейтед экран тайёр бўлгач фойдаланувчи учта такомил сўради:

### Т1. Счёт select'лари QBO tree-select + код + қидирув
- Ҳозир flat select (FX скрипти data-currency/data-balance ўқийди).
- Керак: тур бўйича гуруҳли, счёт КОДИНИ кўрсатадиган, **ном ВА код**
  бўйича typeahead қидирувли dropdown. Native select ярамайди (фақат
  биринчи ҳарф) - Alpine'да махсус қидирувли dropdown компонент.
- Бу Arbitr-014'даги «typeahead 2-босқич» - ҚАЙТА ИШЛАТИЛАДИГАН
  компонент (кейин ҳамма счёт select'ларга). Компонент танланган
  счётнинг currency/balance'ини сақлайди (FX/баланс учун), яширин
  input орқали форма қийматини беради, танланганда change event
  dispatch қилади (FX мантиғи реакция қилсин).

### Т2. Ўзидан ўзига ўтказиш тақиқ (UI)
- Backend аллақачон рад этади (BR-BT-005: манба ≠ манзил).
- UI: битта dropdown'да танланган счёт иккинчисида ТАНЛАНМАЙДИГАН
  (disabled/яширин) бўлади - хато олдиндан олинади.

### Т3. Уч майдонли FX ўзаро боғланиши (бир томони home бўлганда)
Майдонлар: (1) манба сумма, (2) курс, (3) манзил сумма. Боғланиш:
- (1) ўзгарса → (3) жорий курс бўйича қайта ҳисоб.
- (2) курс ўзгарса → (3) қайта ҳисоб.
- (3) ўзгарса → (2) курс тескари ҳисобланади (rate = сумма нисбати,
  scale 6) **ва ўша курс transfer POST'да exchange_rate жадвалига
  ҳужжат санаси билан ТАРИХГА ЁЗИЛАДИ (append)** - мавжуд (ЦБ) курс
  устига ЁЗИЛМАЙДИ, MANUAL ёзув сифатида қўшилади; амалдаги курс =
  энг охиргиси. Тарих сақланади (changeset 033, RateSource).
- GL ЎЗГАРМАЙДИ: base фарқи EXCHANGE_GAIN_OR_LOSS'га ёзилаверади,
  лекин курс сумма нисбатидан олингани учун фарқ АРЗИМАС (0..1 -
  фақат 6-хонали курс яхлитлаш қолдиғи), катта зарар/фойда эмас.
- Курс йўналиши: home-нисбий (home бирлигига неча чет валюта, мавжуд
  Money.exchangeRate конвенцияси). Курс-ёзиш чет валюта учун
  (home тарафи rate=1).
- КЎЛАМ: фақат **бир томони home** бўлган ўтказма (UZS↔валюта -
  Ўзбекистонда деярли ҳамма ҳолат). Икки чет валюта (USD→EUR) кросс-
  курс home-нисбий эмас - у ҳозирча эски икки-курс усулида қолади
  (курс жадвалга ёзилмайди), кейинги иш.

### Ижро тартиби (давоми)
Т1 (қидирувли dropdown компонент) → Т2 (self-guard шу компонентда) →
Т3 (3-майдон боғланиш + POST'да курс upsert). Ҳар бўлакда ./gradlew
test яшил + 375px.

БАЖАРИЛДИ (2026-07-07, ./gradlew test яшил):
- Т1: bank/transferForm.jte'да Alpine қидирувли dropdown - тур бўйича
  гуруҳ, код + ном, код/ном бўйича typeahead. Счёт маълумоти яширин
  DOM'дан (JS-escape хавфсиз). Ҳозирча transferForm ичида; кейин
  shared partial'га чиқарилиб бошқа select'ларга ёйилади.
- Т2: groups() иккинчи dropdown танловини чиқаради - ўзидан ўзига
  танлаб бўлмайди (backend BR-BT-005 устига UI ҳимояси).
- Т3: 3-майдон боғланиш (recalc) - бир томони уй валютаси бўлганда;
  манзил сумма таҳрирланса курс тескари ҳисоб (scale 6). POST'да
  BankTransactionService битта ноёб чет валюта курсини
  ExchangeRateService.upsert (=record MANUAL) билан ҳужжат санасига
  ёзади (икки чет валюта - ёзилмайди). Тест:
  transfer_singleForeign_upsertsRateToCatalog.
- КУРС ТАРИХИ (append-only, changeset 033, 2026-07-07): курс энди
  устига ёзилмайди - ЦБ импорти ва қўлда/ўтказма ўзгартиришлар ҳар
  бири алоҳида ёзув (RateSource CBU/MANUAL). Амалдаги курс = энг
  охирги ёзув (сана, кейин UUIDv7 id); бир кунда 3-4 марта ўзгарса
  ҳам ҳаммаси тарихда. Айнан бир хил курс такрор келса дубль йўқ.
  Тарих экрани (currencyRates.jte) манба устунини кўрсатади.
- ОЧИҚ: интерактив JS фойдаланувчи текшируви кутади; икки чет валюта
  linked эмас (қўлда); dropdown shared partial'га чиқариш (rollout).
