# Payroll Lite - Иш ҳақи (аванс билан) - SPEC

ҲОЛАТ: БАЖАРИЛГАН (23а/23б/23в турткилари, 2026-07-08; кўлам:
«Lite + аванс/қисман тўлаш» - фойдаланувчи тасдиғи).

ДИҚҚАТ: QBO ядросида payroll ЙЎҚ (алоҳида пулли маҳсулот) - бу
лойиҳанинг ИККИНЧИ атайлаб фарқи (engineering-rules.md ЭТАЛОН банди янгиланган).
Шунга бу модул QBO'га эмас, Ўзбекистон кичик бизнес амалиётига
қараб лойиҳаланади - лекин мавжуд ядро нақшлари (PostingService,
detail type орқали счёт топиш, contact dimension, POSTED/reverse)
тўлиқ қайта ишлатилади. Проводкалар: docs/posting-rules.md «Иш ҳақи».

## Мақсад

Ойлик иш ҳақи ҳисоблаш ва тўлаш: Employee каталоги, ойлик ҳисоблаш
ҳужжати (gross → ушланмалар → net + иш берувчи солиғи), аванс/қисман
тўлаш, ойлик ведомость. Тўлиқ statutory ЭМАС: солиқ шкалалари тарихи,
отпуск/касаллик накопленияси, давлат ҳисобот формалари - 1.1+
(roadmap «Кейинги босқичлар»).

## Қатъий қарорлар

- **Employee = ContactType.EMPLOYEE** (янги қиймат) - мавжуд contact
  инфраструктураси (карточка, фаоллик, JE contact dimension) қайта
  ишлатилади; ходим кесимидаги қолдиқ GL contact кесимидан ўқилади
  (AR/AP субледжер услуби). QBO'да ҳам Employee - core name-list.
- **Ҳамма payroll ҳужжатлари ФАҚАТ home валютада** (BR-PYR-001) -
  Lite соддалаштириши, курс/FX умуман йўқ.
- **Ставкалар CompanySettings'да** (фоиз, SETTINGS соҳаси ўзгартиради):
  даромад солиғи % (default 12), жамғариб бориладиган пенсия бадали %
  (default 0.1, ходимдан ушланма), ижтимоий солиқ % (default 12,
  иш берувчи устига). Ҳужжат сатрида ҳисобланган СУММАЛАР сақланади
  (snapshot, Money.exchangeRate услуби) - кейин ставка ўзгарса
  тарихий ҳужжат ўзгармайди.
  ⚠ Ставкалар МУСТАҚИЛ созланма (Lite модели): пенсия бадали даромад
  солиғи ИЧИДАН ажралма эмас, алоҳида ушланма - default'лар билан
  жами ушланма 12.1%. Расмий «12% ичида 0.1% INPS» талқинини истаган
  компания income_tax=11.9 + pension=0.1 деб созлайди (Nargiza-049
  триаж қарори, 2026-07-10: формула net = gross − солиқ − пенсия
  баланс исботининг ядроси - posting-rules.md, ўзгармайди).
- **Счётлар detail type орқали топилади** (мавжуд қоида, инвариант 6):
  PAYROLL_EXPENSES (иккита seed: «Иш ҳақи харажати», «Иш ҳақи солиқ
  харажати»), PAYROLL_CLEARING («Иш ҳақи бўйича мажбурият», ходим
  кесимида), PAYROLL_TAX_PAYABLE («Иш ҳақи солиқлари мажбурияти»).
  Default chart'га seed changeset'да қўшилади (мавжуд базада ҳам).
- **PAYROLL_EXPENSES иккита счёти НОМ бўйича ажратилади**: битта detail
  type'да иккита счёт (иш ҳақи харажати / иш ҳақи солиқ харажати) -
  шунга requireSystemAccount (ягона кутади) ишламайди, buildGlLines
  НОМ бўйича топади (payrollExpenseAccount). ⚠️ Оқибат: бу иккита счёт
  НОМИ тизимга керак - фойдаланувчи уларни қайта номласа payroll
  posting BR-LED-021 («ном бўйича PAYROLL_EXPENSES топилмади») билан
  ТЎХТАЙДИ. Тўлиқ ечим (CompanySettings'да счёт mapping) - 1.1
  statutory кенгайишида; ҳозирча ном инвариант.
- **PAYROLL_CLEARING systemManaged'га киради**: транзфер/қўлда банк
  сатрида танланмайди - тўлов ФАҚАТ PayrollPayment орқали (AR/AP
  услуби; banking.md изоҳи янгиланади). PAYROLL_TAX_PAYABLE
  systemManaged ЭМАС - солиқ тўлови мавжуд Чиқим (Expense) орқали
  (SALES_TAX_PAYABLE прецеденти).
- **Аванс = PayrollPayment ҳисоблашдан олдин**: PAYROLL_CLEARING
  ходим кесимида вақтинча дебет қолдиқ ҳосил қилади; ой охирги
  ҳисоблаш (net кредит) уни ёпади. Тўлов run'га боғланМАЙДИ -
  clearing қолдиғи ўзи ҳақиқат манбаи (Lite соддалиги). Ведомость
  давр бўйича жамлайди.
- POSTED ҳужжат ўзгармайди - фақат reverse (темир қоида 3). DRAFT
  ҳолати бор (invoice қолипи).

## Модел

### Contact кенгайтма (changeset 043 таркибида)

| Майдон | Тип | Изоҳ |
|---|---|---|
| monthly_salary | numeric(19,4) nullable | EMPLOYEE учун ойлик oklad (PayrollRun сатрида prefill; мажбурий эмас) |

ContactType enum'га EMPLOYEE қўшилади (контакт рўйхатида учинчи
табка/филтр, contact.type.EMPLOYEE калити уч тилда).

### PayrollRun (payroll_run) - ойлик ҳисоблаш (changeset 044)

DocumentType PAYROLL_RUN, рақам PAYR-YYYY-NNNNN (spec'даги дастлабки
PAY- префикси BillPayment'га банд экан - 23б имплементациясида PAYR-
танланди, 2026-07-08).

| Майдон | Тип | Изоҳ |
|---|---|---|
| period | varchar(7) NOT NULL | «YYYY-MM» - ҳисобланаётган ой |
| run_date | date NOT NULL | Проводка санаси (одатда ой охири; closing date текшируви шу сана бўйича) |
| status | varchar(10) | DRAFT / POSTED / REVERSED (invoice қолипи) |
| memo | varchar(500) nullable | Изоҳ |

Partial unique: битта period'га биттагина POSTED run (BR-PYR-002;
ux_je_source_active қолипидаги partial index).

### PayrollRunLine (payroll_run_line)

| Майдон | Тип | Изоҳ |
|---|---|---|
| employee_id | UUID NOT NULL (FK contact) | Ходим (EMPLOYEE type, фаол - BR-PYR-003) |
| gross | numeric(19,4) NOT NULL > 0 | Ҳисобланган ойлик (prefill oklad'дан, таҳрирланади) |
| income_tax | numeric(19,4) NOT NULL | Даромад солиғи суммаси (snapshot) |
| pension | numeric(19,4) NOT NULL | Пенсия бадали суммаси (snapshot, ушланма) |
| social_tax | numeric(19,4) NOT NULL | Ижтимоий солиқ суммаси (snapshot, иш берувчи устига) |
| net | numeric(19,4) NOT NULL | Қўлга тегадигани = gross - income_tax - pension |
| class_id | UUID nullable (FK txn_class) | Йўналиш - харажат легига кўчади (invoice қолипи) |

Ҳисоблаш формуласи (сатр сақланганда, HALF_UP 2 хона):
income_tax = gross × ставка; pension = gross × ставка;
social_tax = gross × ставка; net = gross - income_tax - pension.
UNIQUE (run_id, employee_id) - бир run'да ходим бир марта.

### PayrollPayment (payroll_payment + line) - тўлов (changeset 045)

DocumentType PAYROLL_PAYMENT, рақам PAYP-YYYY-NNNNN.

| Майдон | Тип | Изоҳ |
|---|---|---|
| payment_type | varchar(10) | ADVANCE / SALARY - фақат белги (ведомость/рўйхат учун), проводкаси бир хил |
| payment_date | date NOT NULL | Тўлов санаси |
| account_id | UUID NOT NULL | Банк/касса счёти (BANK ёки CASH type, home валютали - BR-PYR-001) |
| status / memo | | Run қолипи |

Line: employee_id (EMPLOYEE, фаол) + amount (> 0). UNIQUE
(payment_id, employee_id).

## Service API (payroll модули)

- `PayrollRunService`: `list(pageable)`, `get`, `saveDraft(form)`,
  `post(id)` → JE (posting-rules «Иш ҳақи»), `reverse(id)`,
  `prefillLines()` - фаол EMPLOYEE'лар oklad билан.
- `PayrollPaymentService`: `list`, `get`, `saveDraft`, `post(id)` →
  JE, `reverse(id)`; `unpaidByEmployee(date)` - PAYROLL_CLEARING
  контакт кесими қолдиғи (форма prefill'и учун).
- `PayrollRegisterService`: `build(period)` - ведомость (қуйида).
- GL'га ёзиш фақат PostingService орқали (темир қоида 2); счётлар
  detail type resolve (инвариант 6).

## Posting

docs/posting-rules.md «Иш ҳақи (Payroll Lite)» бўлими - ЭТАЛОН шу.
Қисқача: Run → Dr харажат (gross, ходим+class) + Dr солиқ харажати
(иш берувчи) / Cr PAYROLL_TAX_PAYABLE (жамланган) + Cr
PAYROLL_CLEARING (net, ходим кесимида); Payment → Dr PAYROLL_CLEARING
(ходим) / Cr банк-касса. Солиқ леглари contact'сиз ва class'сиз;
clearing леглари ходим кесимида, class'сиз (назорат).

## Валидация (BR-PYR)

| Код | Қоида |
|---|---|
| BR-PYR-001 | Payroll ҳужжатлари фақат home валютада (тўлов счёти ҳам home валютали) |
| BR-PYR-002 | Битта ойга (period) биттагина POSTED PayrollRun (DB partial unique ҳам) |
| BR-PYR-003 | Сатрда ходим EMPLOYEE турида ва фаол; gross/amount > 0; сатрлар бўш эмас; бир ҳужжатда ходим такрорланмайди |
| BR-PYR-004 | Run period формати YYYY-MM ва run_date ЎША period ойи ИЧИДА - икки томонлама: на олдинги, на кейинги ойга ўтмайди (Arbitr-047; сторно санаси ҳам period ичида - Arbitr-071/4). Очиқ давр текшируви (closing date) ўз ўрнида. Тўлиқ асос: business-rules.md BR-PYR-004 |
| BR-PYR-005 | Фақат DRAFT payroll ҳужжати (run/тўлов) таҳрирланади/post қилинади (POSTED/REVERSED ўзгармас - темир қоида №3) |
| BR-PYR-006 | Фақат POSTED payroll ҳужжати (run/тўлов) reverse қилинади |

## Экранлар (JTE routes)

- `/payroll` - run'лар рўйхати (period DESC, пагинация 17-банд
  қолипи) + «Янги ҳисоблаш» (FULL форма: period, run_date, сатрлар
  жадвали - «Ходимларни тўлдириш» тугмаси prefill, gross таҳрир,
  ҳисобланган устунлар Alpine билан жонли, Жами сатри) + кўриш
  (сатрлар + JE ҳаволаси + reverse).
- `/payroll/payments` - тўловлар рўйхати (пагинация 17-банд қолипи) +
  янги (тур ADVANCE/SALARY, счёт select Balance билан, сатрлар:
  ходим + сумма, prefill unpaidByEmployee) + кўриш.
- `/reports/payroll-register?period=` - ВЕДОМОСТЬ: ходим кесимида
  давр боши қолдиқ / gross / даромад солиғи / пенсия / net / даврда
  тўланган / давр охири қолдиқ; пастда жамилар. Манба - GL
  (PAYROLL_CLEARING контакт кесими) + run сатрлари. Сарлавҳада
  «Барча суммалар UZS да» (home).
- Меню: Созламалардан ташқарида «Иш ҳақи» гуруҳи (Ҳисоблашлар,
  Тўловлар, Ведомость); «+ Янги»да иккита банд. Контакт рўйхатида
  EMPLOYEE филтри; contact формасида oklad майдони (EMPLOYEE
  танланганда кўринади, Alpine).
- Ҳаммаси mobile-first 375px (.table-wrap).

## Туртки режаси (NAVBAT 23а/23б/23в)

1. **23а**: ContactType.EMPLOYEE + oklad майдони + changeset 043
   (contact устуни + счёт seed'лари) + ставкалар CompanySettings'да +
   PAYROLL_CLEARING systemManaged + banking.md изоҳи.
2. **23б**: PayrollRun (домен/сервис/posting/BR-PYR/форма/кўриш) +
   changeset 044 + тестлар.
3. **23в**: PayrollPayment + changeset 045 + ведомость + меню полиши.

## Тестлар (мажбурий рўйхат)

1. Run post: debit == credit (home); gross = income_tax + pension +
   net; счётлар айнан detail type бўйича; харажат леги ходим+class,
   clearing леги ходим, солиқ леги contact'сиз.
2. Snapshot: ставка ўзгартирилгач эски POSTED run суммалари ва JE
   ўзгармайди; янги run янги ставкада.
3. BR-PYR-002: иккинчи POSTED run ўша ойга рад; reverse қилингач
   қайта POST мумкин.
4. Аванс оқими: аванс → clearing ходимда дебет; run → кредит; ведомость
   қолдиғи = net - аванс; иккинчи (SALARY) тўлов қолдиқни нолга ёпади.
5. Reverse: run ва payment сторнолари кўзгу; ведомость нейтралланади.
6. BR-PYR-001: чет валюта счёти билан payment рад; BR-PYR-003:
   CUSTOMER contact билан сатр рад, нофаол ходим рад.
7. Ведомость йиғиндилари GL қолдиқлари билан айнан тенг (инвариант).
8. VIEWER_AUDITOR ҳужжат яратолмайди (EDIT рухсатисиз - соҳа
   қолипи), кўради.
