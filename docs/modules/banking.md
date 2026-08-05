# Banking - BankTransaction / Reconciliation (8-босқич) - SPEC

## Мақсад
Банк оқими: банк транзакциялари (кирим/чиқим/ўтказма + валюта
конверсияси) ва QBO Reconcile услубидаги солиштириш. Схема манбаси:
docs/old-erp-ideas.md §5 (соддалаштиришлар қуйида), проводкалар:
docs/posting-rules.md «Банк» жадвали. Янги модул: `bank`.
Счётлараро ўтказма тафсилоти: docs/modules/transfer.md; валюта/курс:
docs/modules/multi-currency.md.

## Қатъий қарорлар (тасдиқланган)
- **QBO Reconcile модели**: кўчирма сатрлари киритилмайди - давр +
 якуний қолдиқ киритилади, GL'даги банк сатрлари бир-бир белгиланади,
 фарқ 0 бўлганда якунланади. Old-erp'даги statement line + matching
 олинмади (CSV import кейинги босқичларга).
- **Match жадвали bank модулида** (bank_reconciliation_match) - ledger
 схемасига тегилмайди (қоида №6: ledger ҳеч кимга боғлиқ эмас).
 Ledger'га фақат янги public READ метод қўшилади:
 `reconcilableLines(accountId, toDate)` (Beruniy-perf2'дан кейин
 toggle текшируви учун нуқтавий `reconcilableLine(accountId, lineId,
 toDate)` ҳам бор).
- **Deposit кўп сатрли** (QBO Bank Deposit): битта ҳужжатда бир нечта
 манба сатри (ҳар сатрда манба счёти + сумма + ихтиёрий контакт).
 Типик ҳол: Тушумлар транзитидан банкка.
- **Валюта конверсияси transfer ичида**: иккала томон суммаси/курси
 киритилади, base фарқи EXCHANGE_GAIN_OR_LOSS'га.
- **DRAFT ЙЎҚ**: яратилди = POSTED (тўлов модели), тузатиш reverse.
- **Ҳужжат валютаси банк счётидан келади** (танланмайди, QBO услуби):
 Account.currency бўш бўлса home. Чет валютали банкда курс шарт.

## Entity'лар (changeset 022, bank модули)

### BankTransaction (bank_transaction)
| Майдон | Тип | Изоҳ |
|---|---|---|
| txn_number | varchar(20) unique | DocumentSequence BT-2026-NNNNN (DocumentType.BANK_TXN) |
| type | enum | DEPOSIT / EXPENSE / TRANSFER |
| bank_account_id | UUID (FK account) | Асосий банк: deposit'да қабул қилувчи, expense'да тўловчи, transfer'да МАНБА (BR-BT-002) |
| counterpart_account_id | UUID nullable (FK account) | Transfer'да манзил банк; бошқа турда null |
| txn_date | date | |
| currency + exchange_rate | | Банк счёти валютаси; home'да курс 1 (BR-BT-008) |
| counterpart_amount / counterpart_rate | numeric nullable | Transfer'да манзил томон суммаси/курси (конверсия); бир валютада amount/rate билан тенг |
| total / total_base | numeric(19,4) | Ҳужжат валютасида / home |
| contact_id | UUID nullable (DB FK contact) | Ихтиёрий контрагент (QBO payee) - dimension |
| payment_method_id | UUID nullable (FK payment_method) | Тўлов усули (Arbitr-033, QBO PaymentMethodRef) - чиқим формасида; deposit ҳам қабул қилади (формасида кейин) |
| ref_no | varchar(30) nullable | Ҳужжат/чек рақами (QBO DocNumber - Ref no) |
| status | enum | POSTED / REVERSED |
| memo | varchar(500) | |

### PaymentMethod (payment_method, changeset 036, shared модули)
Тўлов усуллари каталоги (Arbitr-033, QBO PaymentMethod): name
varchar(30) unique + active. QBO'даги Type (CREDIT_CARD/...) атайлаб
олинмаган - credit card кўлами рад этилган (Otabek-001). Усул
ЎЧИРИЛМАЙДИ - active=false (каталог қолипи). Seed: Нақд, Банк
ўтказмаси, Пластик карта. CRUD: /settings/payment-methods (SETTINGS
соҳаси).
BR кодисиз: мавжудлик NotFound, нофаоллик select даражасида, ном
дубли DB unique.

### BankTransactionLine (bank_transaction_line)
DEPOSIT/EXPENSE сатрлари (TRANSFER'да сатр йўқ):
line_no, account_id (манба/харажат счёти, BR-BT-004), amount (ҳужжат
валютасида, мусбат), contact_id nullable (QBO deposit "received from"),
memo.

### BankReconciliation (bank_reconciliation)
| Майдон | Тип | Изоҳ |
|---|---|---|
| account_id | UUID (FK account) | BANK туридаги счёт (BR-RCN-001) |
| statement_date | date | Кўчирма якуний санаси; UNIQUE(account_id, statement_date) - BR-RCN-003 |
| opening_balance | numeric(19,4) | Аввалги COMPLETED reconciliation'нинг closing'идан автоматик; биринчисида қўлда (счёт валютасида) |
| closing_balance | numeric(19,4) | Кўчирмадаги якуний қолдиқ (киритилади) |
| status | enum | IN_PROGRESS / COMPLETED |
| completed_at | timestamptz | |

### BankReconciliationMatch (bank_reconciliation_match)
reconciliation FK + journal_entry_line_id (UUID, DB FK
journal_entry_line - dimension паттерни). **UNIQUE(journal_entry_line_id)
глобал** - сатр фақат бир марта reconcile қилинади (BR-RCN-006).

## Posting (posting-rules «Банк», sourceModule=BANK_TXN, docId=txn id)
- **DEPOSIT**: банк счёти Dt (жами) / ҳар сатр манба счёти Cr
 (ҳужжат валютасида, ҳужжат курсида; сатр contact dimension билан).
- **EXPENSE**: ҳар сатр счёти Dt / банк счёти Cr (жами).
- **TRANSFER (бир валюта)**: манзил банк Dt / манба банк Cr.
- **TRANSFER (конверсия)**: манзил банк Dt (counterpart_amount,
 counterpart_rate) / манба банк Cr (total, exchange_rate); base фарқи
 EXCHANGE_GAIN_OR_LOSS сатри билан тенгланади: олинган base кўп -
 фойда (FX Cr), кам - зарар (FX Dt). Нол фарқ - FX сатр ёзилмайди.
- **Reverse**: оддий GL сторно (reverseBySource) - омбор/денормализация
 йўқ, энг содда тур. Reconcile қилинган транзакция reverse бўлса
 сторноси ҳам кейинги reconcile'да белгиланади (QBO услуби, ҳимоя шарт
 эмас).

## Reconcile оқими (QBO услуби)
1. `start(accountId, statementDate, closingBalance[, openingBalance])`:
 IN_PROGRESS ҳужжат; opening автоматик - ЯНГИ САНАДАН ОЛДИНГИ энг
 сўнгги COMPLETED'нинг closing'идан (ундай давр бўлмаса киритилади).
 Тартибсиз (орқага) бошлаш тақиқ: янги санадан кейинги давр
 аллақачон COMPLETED бўлса BR-RCN-008 (Zumrad-003 - акс ҳолда
 opening «глобал охирги» closing'дан олиниб фарқ ҳеч қачон нолга
 тушмас ёки ёлғон COMPLETED ҳосил бўлар эди).
2. Экранда счётнинг statement_date'гача бўлган POSTED, ҳали reconcile
 қилинмаган GL сатрлари (ledger public read методи орқали) - ҳар
 бирини белгилаш/ечиш (`toggle(reconciliationId, lineId)`).
3. Жонли фарқ = closing − opening − (белгиланган Dt йиғиндиси −
 белгиланган Cr йиғиндиси), счёт валютасида (сатр Money.amount).
4. `complete(reconciliationId)`: фарқ айнан 0 бўлса COMPLETED
 (BR-RCN-005), акс ҳолда хато. COMPLETED ўзгармас; IN_PROGRESS'ни
 бекор қилиш мумкин (match'лар ўчади).

## Валидация (BR-BT, BR-RCN)
| Код | Қоида |
|---|---|
| BR-BT-001 | Сумма/сатр суммаси мусбат |
| BR-BT-002 | Банк счёти BANK туридан, фаол ва postable |
| BR-BT-003 | DEPOSIT/EXPENSE'да камида битта сатр |
| BR-BT-004 | Сатр счёти фаол, postable ва банкнинг ўзи эмас |
| BR-BT-005 | Transfer'да манба ва манзил банк ҳар хил |
| BR-BT-006 | Транзакция санаси шарт |
| BR-BT-007 | Фақат POSTED транзакция reverse қилинади |
| BR-BT-008 | Чет валютали банкда мусбат курс шарт; home'да курс 1 |
| BR-BT-009 | Транзакция тури шарт (форма) |
| BR-BT-010 | Кирим/чиқим сатри тизим-бошқарув назорат счёти эмас (Xorazmiy-012) |
| BR-RCN-001 | Reconciliation фақат BANK туридаги фаол счёт учун |
| BR-RCN-002 | Кўчирма санаси ва якуний қолдиқ шарт |
| BR-RCN-003 | Бир (счёт, сана)га битта reconciliation (409) |
| BR-RCN-004 | Белгилаш/якунлаш фақат IN_PROGRESS'да |
| BR-RCN-005 | Якунлашда фарқ айнан 0 бўлиши шарт |
| BR-RCN-006 | GL сатри аллақачон reconcile қилинган (409, DB unique ҳам) |
| BR-RCN-007 | Фақат шу счётнинг GL'даги (POSTED/REVERSED, кўчирма санасигача) сатри белгиланади - сторно жуфти иккиси ҳам белгиланиб неттоси нолга тушади (DRAFT ҳеч қачон) |
| BR-RCN-008 | Тартибсиз (орқага) бошлаш тақиқ: янги санадан кейинги давр аллақачон COMPLETED (Zumrad-003) |

BR-BT-010 кўлам изоҳи (Xorazmiy-012, BR-TXF-002 нинг deposit/expense
кўзгуси): тизим назорат счётлари (`AccountDetailType.systemManaged` -
AR, AP, INVENTORY, INVENTORY_CLEARING, PAYROLL_CLEARING,
OPENING_BALANCE_EQUITY, RETAINED_EARNINGS) кирим/чиқим сатрида ҳам рад
этилади ва сатр счёт select'ида кўринмайди - қўлда ёзув GL'ни
subledger'сиз (StockMovement, AR/AP aging) ўзгартириб мувофиқликни бузар
эди. Иккита истисно: UNDEPOSITED_FUNDS сатрга РУХСАТ - кирим/чиқим айнан
унинг ўз оқими («Типик ҳол: Тушумлар транзитидан банкка», QBO Bank
Deposit паритети), алоҳида subledger'и йўқ; SALES_TAX_PAYABLE эса
systemManaged рўйхатида умуман йўқ - алоҳида tax payment оқими
қурилгунча ҚҚС тўлови айнан чиқим (expense) орқали қилинади.

Payroll (23а): `PAYROLL_CLEARING` systemManaged'га қўшилди - ҳисобланган
иш ҳақи ходим кесимида шу счётда туради, тўлов ФАҚАТ PayrollPayment
орқали (AR/AP услуби; тўлов оқими 23в'да). Транзфер/қўлда банк сатрида
танланиши мавжуд BR-TXF-002 (transfer) ва BR-BT-010 (кирим/чиқим)
гарови билан автоматик рад этилади - алоҳида қоида шарт эмас.
`PAYROLL_TAX_PAYABLE` эса SALES_TAX_PAYABLE каби systemManaged ЭМАС:
солиқ тўлови мавжуд чиқим (expense) орқали.

## Туртки режаси (ҳар бири алоҳида тасдиқланади)
1. Spec (шу ҳужжат) + BR каталог + changeset 022 + domain entity'лар +
 DocumentType.BANK_TXN + sequence seed.
2. BankTransactionService: deposit (кўп сатрли) / expense / transfer
 (конверсия FX билан) / reverse - тўлиқ тестлар.
3. ReconciliationService + ledger public read методи
 (reconcilableLines) - тўлиқ тестлар.
4. UI: /bank-transactions рўйхат/форма (тур бўйича динамик Alpine),
 /reconciliation (QBO-флоу: checkbox'лар, жонли фарқ, якунлаш),
 sidebar БАНК бўлими, «+ Янги»: Банк транзакцияси.

## Тестлар (мажбурий рўйхат - 2-3-турткиларда)
- Deposit кўп сатрли: банк Dt жами / манбалар Cr, debit == credit;
 UF'дан банкка оқим (тушум → deposit).
- Expense: харажат Dt / банк Cr; контакт dimension.
- Transfer бир валютада (FX сатрсиз); конверсия UZS→USD ва USD→UZS
 (фойда/зарар иккала йўналиш, фарқ EXCHANGE_GAIN_OR_LOSS'га); айнан
 тенг base'да FX сатр йўқ.
- Чет валютали банкда ҳужжат валютаси счётдан келади; курссиз BR-BT-008.
- Reverse ҳар уч тур учун (GL сторно).
- Reconcile: биринчи statement (opening қўлда) → белгилаш → фарқ 0 да
 COMPLETED; фарқ != 0 да BR-RCN-005; иккинчи statement opening'ни
 аввалгисидан олади; сатр икки марта белгиланмайди (BR-RCN-006);
 бошқа счёт сатри рад (BR-RCN-007); бекор қилишда match'лар бўшайди.
- BR-BT-001..006, 008 guard'лари.

## Экранлар (4-туртки)
Sidebar'га янги БАНК бўлими: Транзакциялар (/bank-transactions),
Reconciliation (/reconciliation). «+ Янги»: Банк транзакцияси.
Ҳамма жадвал zebra + .table-wrap, 375px, money формат (Fmt).

### Чиқим - алоҳида экран (Arbitr-033)
Чиқим (EXPENSE) QBO /app/expense паритетидаги алоҳида /expenses
экранига кўчди (transfers нақши): рўйхат (Beruniy-020 fetch йўли +
Arbitr-068 давр/статус/payee/матн филтри), QBO тартибидаги форма (Олувчи | Тўлов счёти +
жонли Balance | катта жонли Сумма | курс (prefill, Arbitr-029) | сана |
Тўлов усули | Ҳужжат рақами | сатрлар (systemManaged'сиз, BR-BT-010) |
Total ҳужжат валютасида + home'да | memo), кўриш + сторно
(/expenses/{id}; EXPENSE бўлмаган id умумий view'га йўналади). Умумий
«Банк транзакцияси» формасида фақат Кирим (DEPOSIT) қолди;
/bank-transactions/new?type=EXPENSE эски линки /expenses/new'га
redirect. Меню: «+ Янги» → «Чиқим (харажат)», сайдбар БАНК → Чиқимлар.
Проводка ЎЗГАРМАГАН (Dt сатр счётлари / Cr банк) - posting-rules.md
ўша. Кирим (Bank deposit) алоҳида экрани - КЕЙИН (фойдаланувчи қарори).
