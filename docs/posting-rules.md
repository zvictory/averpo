# Проводка қоидалари (posting rules)

Эталон - QuickBooks Online. Счёт рақами ихтиёрий (QBO услуби), шунинг
учун қоидалар кодга эмас, **Detail Type**'га боғланади: тизим счётлари
`AccountRepository.findByDetailType(...)` орқали топилади. Бир detail
type'да бир нечта счёт бўлиши мумкин бўлган жойда (масалан банклар)
счётни ҳужжатнинг ўзи кўрсатади (QBO'да invoice'даги deposit account
каби).

Ҳар бир янги ҳужжат тури аввал шу файлга қоида сифатида қўшилади,
кейин код ёзилади. PostingService шу қоидаларга қатъий амал қилади.

## Тизим счётлари (default chart'да биттадан яратилади)

| Detail Type | Роль |
|---|---|
| ACCOUNTS_RECEIVABLE | AR назорат счёти |
| ACCOUNTS_PAYABLE | AP назорат счёти |
| INVENTORY | Товар-моддий заҳиралар (AVCO қиймати) |
| INVENTORY_CLEARING | Landed cost клиринг (Averpo кенгайтмаси) |
| UNDEPOSITED_FUNDS | Тушумлар транзити |
| OPENING_BALANCE_EQUITY | Очилиш қолдиқлари |
| RETAINED_EARNINGS | Йил ёпилиши |
| SALES_OF_PRODUCT_INCOME | Товар сотув даромади |
| SUPPLIES_MATERIALS_COGS | Товар таннархи (COGS) |
| OTHER_COSTS_OF_SERVICE_COS | Inventory камомад/ортиқча (shrinkage) |
| EXCHANGE_GAIN_OR_LOSS | Валюта курси фарқи - битта счёт: фойда кредит, зарар дебет |
| SALES_TAX_PAYABLE | ҚҚС контрол счёти - битта счёт: сотувда кредит (output), харидда дебет (input); қолдиқ нетто тўланадиган ҚҚС (QBO VAT control услуби, docs/modules/tax.md) |

## Сотув (Sales - docs/modules/sales.md)
| Ҳужжат | Дебет | Кредит | Изоҳ |
|---|---|---|---|
| Invoice post (товар) | ACCOUNTS_RECEIVABLE | SALES_OF_PRODUCT_INCOME | ҳужжат курсида; AR - gross (ҚҚСли), даромад - net |
| Invoice post (ҚҚС) | - | SALES_TAX_PAYABLE | ставка кесимида жамланган сатр(лар); нол жами ёзилмайди (tax.md) |
| Invoice post (COGS) | SUPPLIES_MATERIALS_COGS | INVENTORY | AVCO нархда, home валютада |
| Invoice payment | банк/касса/UNDEPOSITED_FUNDS (ҳужжатда танланади) | ACCOUNTS_RECEIVABLE | тўлов курсида |
| Курс фарқи (фойда) | ACCOUNTS_RECEIVABLE | EXCHANGE_GAIN_OR_LOSS | realized |
| Курс фарқи (зарар) | EXCHANGE_GAIN_OR_LOSS | ACCOUNTS_RECEIVABLE | realized |
(Қайтариш ҳужжатлари - қуйида алоҳида «Қайтариш (Returns)» бўлими.)

- Манба модуллар (BR-LED-012 idempotency): Invoice post - INVOICE/
  invoice id (AR + даромад + COGS битта JE'да; SERVICE сатрда COGS
  йўқ); тушум - INVOICE_PAYMENT/payment id; realized курс фарқи ҳар
  allocation учун АЛОҲИДА JE - RECEIPT_ALLOCATION/allocation id (нол
  фарқ - JE ёзилмайди). Фарқ base = allocation × (тўлов курси -
  invoice курси): мусбат - фойда (AR Dt / gain Cr), манфий - зарар
  (тескари). Даромад сатри - invoice сатридаги счёт (item default),
  COGS/INVENTORY - home таннархда. AR - тизим счёти (BR-LED-021).
  ҚҚС сатрлари ўша INVOICE JE ичида (алоҳида манба модул йўқ);
  net/gross бўлиниши ва inclusive режим: docs/modules/tax.md.
  Тафсилот: docs/modules/sales.md.

## Харид (Purchases - docs/modules/purchases.md)
| Ҳужжат | Дебет | Кредит | Изоҳ |
|---|---|---|---|
| Bill post (товар) | INVENTORY | ACCOUNTS_PAYABLE | AVCO'га НЕТТО (ҚҚСсиз) қиймат киради; AP - gross |
| Bill post (хизмат/харажат) | тегишли EXPENSE счёти (ҳужжатда танланади) | ACCOUNTS_PAYABLE | дебет - net |
| Bill post (ҚҚС) | SALES_TAX_PAYABLE | - | ставка кесимида жамланган сатр(лар); ҳисобга олинадиган ҚҚС таннархга кирмайди (tax.md) |
| Bill post (landed cost хизмати) | INVENTORY_CLEARING | ACCOUNTS_PAYABLE | ташиш, божхона ва ҳ.к. |
| Landed cost тақсимоти | INVENTORY | INVENTORY_CLEARING | receipt'га тақсимланади; сотилган қисм: COGS Dt |
| Bill payment | ACCOUNTS_PAYABLE | банк (ҳужжатда танланади) | тўлов курсида |
| Курс фарқи (фойда) | ACCOUNTS_PAYABLE | EXCHANGE_GAIN_OR_LOSS | realized |
| Курс фарқи (зарар) | EXCHANGE_GAIN_OR_LOSS | ACCOUNTS_PAYABLE | realized |

- Манба модуллар (BR-LED-012 idempotency): Bill post - BILL/bill id;
  тўлов - BILL_PAYMENT/payment id; realized курс фарқи ҳар allocation
  учун АЛОҲИДА JE - PAYMENT_ALLOCATION/allocation id (тўлов билан
  бирга ҳам, кейинги allocation'да ҳам бир хил йўл; нол фарқ - JE
  ёзилмайди); landed cost тақсимоти - LANDED_COST/ҳужжат id (қолган
  қисм item asset счётига Dt, сотилган қисм SUPPLIES_MATERIALS_COGS
  Dt, жами INVENTORY_CLEARING Cr; нол улуш сатр ёзилмайди). ITEM
  сатрлари item'нинг ўз inventory asset счётига (adjustment паттерни),
  AP/INVENTORY_CLEARING - тизим счётлари (BR-LED-021). ҚҚС сатрлари
  ўша BILL JE ичида (алоҳида манба модул йўқ); net/gross бўлиниши ва
  inclusive режим: docs/modules/tax.md. Тафсилот:
  docs/modules/purchases.md.
- Атайлаб қилинган соддалаштириш (practical expedient, QBO услуби):
  cogs_share БАРЧА ейилган улушни қамрайди - сотув бўлмаган чиқимлар
  (write-off, камомад/shrinkage, ички сарф) улуши ҳам COGS'га боради,
  чунки чиқим сабабини излаб улушни счётларга бўлиш ўта мураккаб
  (IAS 2 га зид эмас - cost-benefit асосидаги танлов). Йирик камомад
  улуши бўлса бухгалтер қўлда JE билан COGS'дан тегишли харажат/зарар
  счётига кўчиради.

## Қайтариш (Returns - docs/modules/returns.md)

Учала ҳужжат ҳам ўз санаси/курси билан POSTED бўлади, тузатиш фақат
reverse. Ҳужжат валютаси манба оқим валютаси каби эркин.

### CreditMemo (мижоз кредит-нотаси) - манба CREDIT_MEMO/id, битта JE
| Қисм | Дебет | Кредит | Изоҳ |
|---|---|---|---|
| Сатрлар (net) | сатр даромад счёти (invoice кўзгуси) | - | даромад қайтади |
| ҚҚС (output қайтиши) | SALES_TAX_PAYABLE | - | ставка кесимида, tax.md механизми |
| Жами (gross) | - | ACCOUNTS_RECEIVABLE | мижоз кредити - AR камаяди |
| Inventory қайтими | INVENTORY | SUPPLIES_MATERIALS_COGS | home валютада, қайтим таннархида (қуйида) + StockMovement IN (омбор ҳужжатда) |

### RefundReceipt (пул қайтариш) - манба REFUND_RECEIPT/id, битта JE
CreditMemo билан бир хил, фарқи: AR ўрнига **пул счёти** -
Cr банк/касса (ҳужжатда танланади, gross, тизим счёти эмас).
AR'га тегмайди, application йўқ - тугал ҳужжат.

### VendorCredit (таъминотчи кредит-нотаси) - манба VENDOR_CREDIT/id, битта JE
| Қисм | Дебет | Кредит | Изоҳ |
|---|---|---|---|
| Жами (gross) | ACCOUNTS_PAYABLE | - | AP камаяди |
| Сатрлар: хизмат/харажат (net) | - | ўша EXPENSE счёти | харажат қайтади |
| ҚҚС (input қайтиши) | - | SALES_TAX_PAYABLE | ставка кесимида |
| Inventory қайтарилиши | - | INVENTORY (сиёсат таннархида) | StockMovement OUT + фарқ қуйида |
| Қайтим фарқи (net − сиёсат таннархи) | OTHER_COSTS_OF_SERVICE_COS (мусбатда Cr, манфийда Dt) | | балансловчи сатр, нол бўлса ёзилмайди |

### Application (кредитни ҳужжатга қўллаш) - GL'га ЁЗИЛМАЙДИ*
CreditMemo → invoice ва VendorCredit → bill қўллаш subledger
ҳаракати: иккала ҳужжат ҳам ўз JE'сини аллақачон ёзган, AR/AP
контрол счёти тўғри. *Истисно - realized курс фарқи: allocation
base фарқи (allocation × (кредит курси − ҳужжат курси)) бўлса
мавжуд нақшдаги АЛОҲИДА JE - манба CREDIT_APPLICATION/allocation id
(AR учун) ва VENDOR_CREDIT_APPLICATION/allocation id (AP учун);
нол фарқ - JE ёзилмайди. Бир валюта шарти: кредит фақат ўз
валютасидаги ҳужжатга қўлланади (BR-RET, QBO услуби).

### Inventory қайтим таннархи (сиёсат)
- **CreditMemo/RefundReceipt (кирим)**: асл invoice сатри ҳавола
  қилинган бўлса - ўша сотув StockMovement'ининг бирлик таннархи;
  ҳаволасиз - жорий сиёсат таннархи (AVCO ўртача; FIFO'да янги
  қатлам шу нархда киради). Марж бузилмасин деган мувозанат.
- **VendorCredit (чиқим)**: ҳамиша жорий сиёсат таннархида чиқади
  (adjustment нақши - AVCO/FIFO бутунлиги ҳужжат нархидан устун);
  ҳужжат net'и билан фарқ OTHER_COSTS_OF_SERVICE_COS'га (онгли
  соддалаштириш, cogs_share изоҳидаги услуб).

## Омбор (Inventory - docs/modules/inventory.md)
| Ҳужжат | Дебет | Кредит | Изоҳ |
|---|---|---|---|
| Adjustment (камайиш) | OTHER_COSTS_OF_SERVICE_COS (shrinkage) | INVENTORY | инвентаризация |
| Adjustment (кўпайиш) | INVENTORY | OTHER_COSTS_OF_SERVICE_COS | |
| Transfer (омборлараро) | - | - | GL проводка ЙЎҚ |
| Estimate / PurchaseOrder | - | - | GL проводка ЙЎҚ (non-posting, estimates-po.md) |

- INVENTORY томони - item'нинг ўз inventory asset счёти (BR-ITM-006/007
  кафолатлайди), shrinkage - OTHER_COSTS_OF_SERVICE_COS тизим счёти
  (топилмаса BR-LED-021). sourceModule = INVENTORY, sourceDocumentId =
  movement id. Нол қийматли adjustment (avg cost 0) GL'га ёзилмайди -
  BR-LED-002 XOR қоидасига зид бўлар эди, миқдор ҳаракати ўз кучида.
- **Ҳужжатли Adjustment (Arbitr-093, 2026-07-11)**: кўп сатрли
  инвентаризация акти БИТТА JE билан боради - леглар: кўпайиш
  сатрлари item'нинг ўз inventory счётига Dr (сатр кесимида) /
  жамланган Cr OTHER_COSTS_OF_SERVICE_COS; камайишлар тескари;
  аралаш актда иккала жуфтлик битта JE ичида. sourceModule =
  INVENTORY, sourceDocumentId = АКТ id (movement id ЭМАС - акт
  ҳаракатлари reference орқали боғланади). Нол қийматли сатр легга
  кирмайди (юқоридаги қоида). Ҳужжатли Transfer аввалгидек GL'сиз.
  Эски бир-амаллик ёзувлар тарихий - қоидалари ўз кучида.

## Банк (Banking - docs/modules/banking.md)
| Ҳужжат | Дебет | Кредит |
|---|---|---|
| Кирим (бошқа) | банк счёти | манба счёти |
| Чиқим (харажат) | тегишли EXPENSE счёти | банк счёти |
| Ўтказма (бир хил валюта) | манзил Balance Sheet счёти | манба Balance Sheet счёти |
| Ўтказма (валюта фарқли) | манзил счёти | манба счёти, base фарқи EXCHANGE_GAIN_OR_LOSS |

Ўтказма (Transfer, docs/modules/transfer.md, Arbitr-022): манба ва
манзил ҳар қандай Balance Sheet счёти (Актив/Мажбурият/Капитал, BR-TXF-001) -
фақат банк эмас; кирим/чиқим эса банк оқими. Пул манбадан чиқади (Cr) →
манзилга киради (Dt).

## Очилиш қолдиқлари (Opening balances)
| Ҳужжат | Дебет | Кредит |
|---|---|---|
| Актив счёт очилиши | счёт | OPENING_BALANCE_EQUITY |
| Пассив/капитал счёт очилиши | OPENING_BALANCE_EQUITY | счёт |

- Манба: sourceModule = OPENING_BALANCE, sourceDocumentId = account id -
  BR-LED-012 idempotency ва ux_je_source_active index бир счётга икки
  марта opening balance киритишни автоматик тўсади. Хато киритилган
  бўлса entry reverse қилинади, кейин қайта киритиш мумкин.
- Сумма счёт валютасида киритилади (QBO услуби); чет валюта счётида
  курс шарт, OPENING_BALANCE_EQUITY сатри доим home валютада
  (baseAmount тенг).
- Манфий сумма томонларни алмаштиради (масалан, overdraft банк).
- AR/AP счётларига бу йўл ёпиқ - уларнинг қолдиғи invoice/bill
  ҳужжатлари орқали киради (QBO услуби, contact'га боғлиқ бўлиши учун).

## Class кўчиши (class-tracking.md - таҳлилий тег, суммага таъсир ЙЎҚ)
- Ҳужжат сатридаги class posting'да ўша сатрнинг GL сатрига айнан
  кўчади (Invoice даромад леги + шу сатрдан келиб чиққан COGS леги;
  Bill сатр леги; Deposit/Expense сатр леги; қўлда JE сатри).
- Назорат/жами сатрлар (AR, AP, банк жами) class ОЛМАЙДИ - уларда бир
  нечта class аралашади; QBO ҳам control томонни тегсиз қолдиради.
- Техник сатрлар (EXCHANGE_GAIN_OR_LOSS, penny rounding) ва ставка
  кесимида ЖАМЛАНГАН ҚҚС леглари class'сиз.
- Сторно class'ни айнан кўчиради (P&L by Class'да ҳам нейтралланиши
  учун); нофаол class сторнони ТЎСМАЙДИ (тарихий тег, валюта нақши).
- Transfer/payment/opening balance ҳужжатларида class умуман йўқ.
  ДИҚҚАТ - далил тузатилди (Otabek-008, 2026-07-08): Finance.xsd'да
  Transfer'да ClassRef АСЛИДА БОР (:10316) - бу ОНГЛИ ФАРҚ: бизда
  class фақат P&L кесими (P&L by Class), transfer эса соф Balance
  Sheet ҳужжати - class QBO'да у ерда Balance Sheet by Class учун
  ишлатилади, биз у ҳисоботни қурмаймиз. Зарурат чиқса (BS by Class)
  transfer'га header ClassRef алоҳида туртки бўлади.

## Сотув чеки (SalesReceipt - сотув + тўлов бир ҳужжатда; docs/modules/sales.md)

| Ҳужжат | Дебет | Кредит |
|---|---|---|
| SalesReceipt POST | Банк/касса (gross) | Даромад (net, сатр кесимида) + SALES_TAX_PAYABLE (ҚҚС жамланган) |
| Inventory сатри (қўшимча) | COGS | INVENTORY (сиёсат таннархи) |

- Invoice'нинг айнан кўзгуси, фақат AR ўрнига тўғри банк/касса - AR
  умуман қатнашмайди, allocation ҳам йўқ (тўлов дарҳол).
- Манба: SALES_RECEIPT; ҳужжат рақами SR-YYYY-NNNNN.
- Банк/касса счёт валютаси ҳужжат валютасига тенг бўлиши шарт
  (BR-SR каталогида; QBO DepositToAccount қоидасига мос) - FX фарқи
  туғилмайди.
- Class invoice қолипида: даромад/COGS леглари сатрдан, банк жами
  сатри class'сиз.
- StockMovement OUT invoice услубида (омбор сатрда танланади).

## Иш ҳақи (Payroll Lite - docs/modules/payroll.md; QBO'дан атайлаб фарқ №2)

| Ҳужжат | Дебет | Кредит |
|---|---|---|
| PayrollRun POST (ойлик ҳисоблаш) | PAYROLL_EXPENSES gross (ҳар ходим сатри, contact + class) ва PAYROLL_EXPENSES ижтимоий солиқ (иш берувчи) | PAYROLL_TAX_PAYABLE (даромад солиғи + пенсия бадали + ижтимоий солиқ, жамланган) ва PAYROLL_CLEARING net (ҳар ходим кесимида) |
| PayrollPayment POST (аванс/ойлик тўлови) | PAYROLL_CLEARING (ҳар ходим кесимида) | Банк/касса |

- Баланс исботи: gross + ижтимоий == (даромад солиғи + пенсия +
  ижтимоий) + net, чунки net = gross - даромад солиғи - пенсия.
- Ҳамма payroll ҳужжатлари ФАҚАТ home валютада (BR-PYR каталогида;
  ДИҚҚАТ: BR-PAY префикси BillPayment'га банд - шунга PYR) -
  курс/FX йўқ.
- PAYROLL_CLEARING systemManaged: транзфер/қўлда банк сатрида
  танланмайди - тўлов фақат PayrollPayment орқали (AR/AP услуби).
  PAYROLL_TAX_PAYABLE эса systemManaged ЭМАС - солиқ тўлови мавжуд
  Чиқим (Expense) орқали (SALES_TAX_PAYABLE прецеденти).
- Солиқ леглари contact'сиз ва class'сиз (бюджетга жами); харажат
  леглари ходим (contact) ва class кесимида; PAYROLL_CLEARING
  леглари ходим кесимида, class'сиз (назорат сатри).
- Аванс: PayrollPayment ҳисоблашдан олдин ҳам мумкин - clearing
  ходим кесимида вақтинча дебет қолдиқ беради, ой охирги run уни
  ёпади (ведомость шуни кўрсатади).
- Reverse иккала ҳужжатда стандарт сторно (қоида 3).

## Таҳрирлаш ва ўчириш (posted-edit - docs/modules/posted-edit.md)

- ТАҲРИР: битта атомик транзакцияда эски ҳужжатга СТОРНО JE + янги
  версия ҳужжатининг ўз қоидаси бўйича ЯНГИ JE. Иккиси ҳам ЯНГИ
  ВЕРСИЯ санасида (сана очиқ даврда - BR-EDT-004). Ҳар иккала JE
  ўз ичида балансланади (қуйидаги 1-инвариант).
- ЎЧИРИШ: фақат сторно JE (GL'да нетто-ноль жуфт из қолади),
  ҳужжат deleted белгиси билан барча кўринишлардан яширилади.
- Автоматик изоҳлар: сторно JE'да «Таҳрир: <рақам> v(n)→v(n+1)»
  ёки «Ўчириш: <рақам>»; янги JE'да «<рақам> v(n+1) (таҳрир)».
- Механизм мавжуд reverse оқимини ишлатади - PostingService'га
  янги ёзиш йўли ОЧИЛМАЙДИ (темир қоида 2/3 сақланади).

## Инвариантлар
1. Ҳар entry'да sum(debitBase) == sum(creditBase) - home валютада.
2. Ҳар line'да debit XOR credit (иккиси бирга эмас, иккиси нол эмас).
3. Фақат postable=true счётларга проводка мумкин.
4. entry_date ёпилган даврга тушмаслиги керак (BR-LED-020, closing date
   CompanySettings'да; сторно санаси ҳам очиқ даврда бўлиши шарт).
5. Home currency биринчи POSTED entry'дан кейин ўзгартирилмайди.
6. Тизим detail type счётини топа олмаса (йўқ ёки бир нечта) -
   аниқ хато: фойдаланувчи счётни белгилаши сўралади.
7. Class (Йўналиш) GL суммаларини ўзгартирмайди - debit == credit
   тенгламаси class билан/class'сиз бир хил (class-tracking.md).
