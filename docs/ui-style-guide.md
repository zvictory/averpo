# Averpo UI стил-гид - экран қолиплари каталоги

Ҳолат: (Arbitr-009). Манба - мавжуд JTE экранларнинг ўзи:
ҳар қолипга биттадан намуна `файл:қатор` ҳаволаси берилган. Янги экран
ёзишдан аввал шу гид ва `docs/modules/ui-navigation-display.md` (row-click,
валюта/бирлик кўрсатиш, аниқлик қоидалари spec'и) ўқилади. Қолипдан
атайлаб четлашиш - аввал шу файлга ёзилади, кейин кодга.

Умумий темир қоидалар (engineering-rules.md'дан UI'га тегишлилари):
- Ҳар матн i18n калит орқали, УЧАЛА файлга (`messages.properties`,
 `_en`, `_ru`). Кодда қаттиқ ёзилган матн тақиқ.
- Mobile-first: ҳар экран 375px кенгликда ишлайди (7-бўлим).
- Пул экранда ҳеч қачон «яланғоч» эмас, миқдор доим бирлиги билан (5-бўлим).
- SPA framework йўқ: JTE + HTMX + Alpine.js холос.

## 0. Иккита layout - қачон қайси бири

| Layout | Қачон | Намуна |
|---|---|---|
| `layout/main.jte` | Рўйхат, кўриш, ҳисобот, каталог ва содда формалар - sidebar билан | `src/main/jte/purchase/bills.jte:20` |
| `layout/form.jte` | Ҳужжат формалари (QBO transaction form): юқорида қора бар + ✕, пастда sticky «Сақлаш» бари | `src/main/jte/purchase/billForm.jte:21` |

`form.jte` тузилмаси: `.formbar` (`src/main/jte/layout/form.jte:23`),
контент `.formmain` (`:30`), sticky `.formfooter` (`:33`) - actions
блокида чапда ghost Бекор (Penguin, 4-бўлим), ўнгда draft/post
тугмалари (`src/main/jte/purchase/billForm.jte:154-159`).

`login.jte` layout'сиз мустақил саҳифа - бу атайлаб (кириш ҳолатида
sidebar кераксиз); UI-LIB класслари унда ишламайди (main.jte style
блоки йўқ), ўз inline услуби бор. `shared/error.jte` main layout'да.

## 1. Жадвал (Penguin)

Ҳамма `<table>` Penguin услубида (Arbitr-120 свипи; реф:
`docs/penguin-refs/table.html`; эски `.table-wrap`/element-CSS/
`.menu` оиласи ўчирилган). Каноник шакл БИТТА жойда -
`shared/table.jte` фрагменти:

```
@template.shared.table(cls = "min-w-[860px]", c = @`
 <thead>...</thead>
 <tbody>...</tbody>
`)
```

- **Ўрам** (фрагмент ичида): `overflow-hidden w-full overflow-x-auto
 rounded-radius border border-outline dark:border-outline-dark` -
 мобилда жадвал скролл бўлади; `cls`'га `min-w-[NNNpx]` берилади
 (эски inline min-width ўрни), `wrap`'га ўрам қўшимчаси (`max-w-*`).
- **Катак услуби катакка ёзилмайди**: зич паддинг (`px-3 py-2`),
 thead (`bg-surface-alt text-on-surface-strong font-semibold` + dark),
 tbody ажратгичлар (`divide-y divide-outline`), tfoot жами қатори
 (`font-semibold text-on-surface-strong bg-surface-alt` + dark) -
 барчаси table тегидаги `[&_th]/[&_td]/[&_thead]/[&_tbody]/[&_tfoot]`
 arbitrary variant'ларида, фрагментда.
- **Zebra + hover + row-click (QBO одати)** - table тегида БИР нуқтада:
 zebra `[&_tbody_tr:where(:nth-child(even))]:bg-primary/5`
 (+ dark `primary-dark/10`; `:where` - hover устун туриши учун),
 hover `[&_tbody_tr:hover]:bg-surface-alt` (+ dark), босиладиган
 қатор `[&_tr[data-href]]:cursor-pointer` +
 `[&_tbody_tr[data-href]:hover]:bg-primary/10` (+ dark `/20`) -
 кўкроқ hover «босса бўлади» дегани. Ҳар tr'га zebra класси ёзиш ТАҚИҚ.
- **Матн аниқлиги (фойдаланувчи талаби)**: устун сарлавҳаси
 thead канонидан кучли (on-surface-strong); ПУЛ/СОН/ҚИЙМАТ ва ҳужжат
 рақами катаги: `text-right whitespace-nowrap tabular-nums
 text-on-surface-strong dark:text-on-surface-dark-strong` (сарлавҳада
 фақат `text-right`); оддий катак table'дан `text-on-surface` мерос
 олади - класс керакмас; ХИРА тон (`text-current/60`) фақат чинакам
 иккиламчи изоҳга (subtotal ЛАБЕЛИ, сана ости изоҳи) - қийматга ҳеч
 қачон. Марказ (амал устуни): `text-center`.
- **Жадвал ичи ҳаволаси**: ҳужжат рақами каби оддий `<a>` - канон
 фрагментнинг `[&_td>a]:font-medium [&_td>a]:text-primary` (+ dark)
 variant'и рангаклайди, underline йўқ (QBO услуби). Жадвалдан
 ТАШҚАРИДАГИ матн ҳаволаси канони: `text-primary underline
 dark:text-primary-dark`.
- **Уч нуқтали ⋮ меню**: `@template.shared.rowMenu(...)` - Penguin
 dropdown (реф: `docs/penguin-refs/dropdown-menu.html`), панел
 position:fixed (ўрам overflow'и кесмасин), бандлар услуби панел
 тегидаги `[&_a]/[&_button]` variant'ларида - бандга класс ёзилмайди
 (эски `.menuitem` ўчирилган), фақат `<a href>`/`<button>` берилади.
 Ўрам div'ида `data-rowmenu` - row-click панел ичини четлаб ўтади.
- **Бўш ҳолат**: жадвал ўрнига `<p class="text-current/60">` + i18n
 калит (`src/main/jte/purchase/bills.jte`).
- **Статус badge**: `@template.shared.badge(value = st.name, msg =
 msg)` - чип markup'и ва ранг харитаси фрагмент ичида БИР жойда
 (badge.jte), экранда ранг танланмайди. Тўлов ҳолати:
 `family = "paystatus"`; бошқа оилалар: est.status / po.status /
 rcn.status.
- **Погона (ҳисобот дарахти)**: катакда `pl-4!` / `pl-7!` (important -
 канон `[&_td]:px-3` селектор кучидан устун бўлиши учун).
- **Жадвал ичида йиғинди ҳисоблаш**: JTE `!{...}` аккумулятор массиви
 (`src/main/jte/purchase/apAging.jte`;
 `src/main/jte/ledger/journalEntryView.jte`).
- **Саҳифалаш**: `@template.shared.pagination(...)` - Penguin ghost
 линклар + Penguin select ҳажм танлагичи (реф:
 `docs/penguin-refs/pagination.html`), ҳисоб сатри `text-on-surface`;
 механика (25/50/100/200, filterQuery, биринчи/охирги) ўзгармаган.

## 2. Форма (Penguin)

Ҳамма форма контроли Penguin utility йиғимида (Arbitr-121 свипи;
рефлар: `docs/penguin-refs/text-input.html`, `select.html`,
`checkbox-radio.html`; эски компенсация input/select/button қоидалари
ва `.cols2`/`.drawer` оиласи ўчирилган). Каноник шакллар:

- **Майдон (label ичида назорат)** - мавжуд nesting сақланади:
 ```
 <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
 Ёрлиқ матни
 <input/select/textarea class="[НАЗОРАТканони]">
 </label>
 ```
- **НАЗОРАТ канони** (input/select/textarea учун БИР ХИЛ):
 ```
 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2
 text-sm text-on-surface focus-visible:outline-2
 focus-visible:outline-offset-2 focus-visible:outline-primary
 disabled:cursor-not-allowed disabled:opacity-75
 dark:border-outline-dark dark:bg-surface-dark-alt/50
 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark
 ```
 Зич жой (line-item катаги, инлайн-таҳрир): `py-2` ўрнига `py-1.5`;
 эни контекстдан: `w-full` / `w-28 text-right` (сон) / `max-w-56`.
 Select native стрелкали (appearance-none ЙЎҚ - реф изоҳи).
- **Checkbox**: native input + accent (реф мослашуви):
 `<label class="flex items-center gap-2 text-sm font-medium
 text-on-surface dark:text-on-surface-dark"><input type="checkbox"
 class="size-4 accent-primary dark:accent-primary-dark"> Матн</label>`.
- **Ёрдам/изоҳ матни** (майдон ости): `text-xs text-current/60`
 (иккиламчи изоҳдан бошқасига хира тон ТАҚИҚ - 120 қоидаси).
- **✕ сатр ўчириш** (line-item): ghost icon-button:
 `cursor-pointer rounded-radius px-2 py-0.5 text-base leading-none
 text-on-surface/60 transition hover:bg-danger/10 hover:text-danger
 focus-visible:outline-2 focus-visible:outline-offset-2
 focus-visible:outline-danger dark:text-on-surface-dark/60`.
- **Икки устун жуфти**: `grid grid-cols-1 gap-[.9rem] md:grid-cols-2`
 (эски `.cols2` ўрни; formFields'да breakpoint 520px -
 `min-[521px]:grid-cols-2`).
- **CSRF**: ҲАР POST форма бошида `@template.shared.csrf(csrf = csrf)`
 (`src/main/jte/purchase/billForm.jte`) - hidden token'ни фрагмент
 чиқаради, қўлда input ёзилмайди.
- **Ҳужжат формаси бош майдонлари**: `flex flex-wrap items-end gap-4`
 қатори; майдон label'лари юқоридаги канонда.
- **Содда форма картаси** (каталог/inventory) - Penguin card ўрами:
 `grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline
 bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark`
 (тўқ режимда карта ичи яхлит тўқ; боди 122 гача оч -
 пагинация/палитра прецеденти).
- **Пул киритиш**: `class="money" inputmode="decimal"` - ёзиш пайтида
 минг гуруҳлаш, submit'да тозалаш `static/js/money-input.js` (иккала
 layout ҳам улайди). Курс майдонлари `.money`'сиз, фақат
 `inputmode="decimal"` (гуруҳлаш кераксиз).
- **HTMX сатр қўшиш** (ҳужжат линиялари): `tbody id="lineRows"` +
 тугмада `hx-get`/`hx-vals`(кейинги индекс)/`hx-target`/`hx-swap="beforeend"`
 (`src/main/jte/ledger/journalEntryForm.jte:51-64`); қатор partial'и
 `data-index` билан (`src/main/jte/ledger/lineRow.jte:11`).
- **HTMX боғлиқ partial**: select ўзгарса бошқа блок янгиланади -
 vendor → очиқ bill'лар (`src/main/jte/purchase/paymentForm.jte:31-34`);
 partial икки жойда қайта ишлатилади
 (`src/main/jte/purchase/paymentOpenBills.jte:8-10`).
- **Инлайн-таҳрир** (кичик каталоглар: units, warehouses, categories):
 ҳар қатор ўз мини-`<form>`и, шу жойда Сақлаш
 (`src/main/jte/item/units.jte:43-58`). Бундай жадвалларга row-click
 қўйилмайди (spec: атайлаб).
- **Филтр формаси**: GET, `mb-4 flex flex-wrap items-end gap-3`;
 филтр label'лари канонда + `min-w-[180px]` оиласи; select филтрлар
 `onchange="this.form.submit"` (`src/main/jte/inventory/balances.jte`).
- **Филтр чипи** (drill-down'дан келинганда): кулранг матн + «Филтрни
 олиб ташлаш» ҳаволаси; бошқа филтр submit'ида йўқолмаслиги учун hidden
 input (`src/main/jte/inventory/movements.jte:29-47`;
 `src/main/jte/purchase/bills.jte:52-59`).
- **Курс prefill**: валюта ўзгарганда `/exchange-rates/lookup` дан курс
 олинади, ФАҚАТ бўш/`1` турган майдон тўлдирилади - қўлда киритилган
 курс устидан ёзилмайди (`src/main/jte/purchase/billForm.jte:129-151`).
- **Валюта кўрсаткичи майдон ёнида**: `data-curtag` спанлари, валюта
 танлови ўзгарганда JS янгилайди
 (`src/main/jte/purchase/billForm.jte:103-104,141`); контактда credit
 limit валютаси (`src/main/jte/contact/form.jte:121,127-139`); статик
 home коди - kulrang спан (`src/main/jte/item/form.jte:118`).
- **Бирлик кўрсаткичи**: item select option'ида `data-unit`, миқдор
 ёнидаги спанни JS тўлдиради
 (`src/main/jte/inventory/adjustmentForm.jte:38-42,94-107`).
- **Кўриш саҳифаси header картаси**: Penguin card + auto-fit grid
 utility йиғими (4а-бўлим; `src/main/jte/ledger/journalEntryView.jte:33`).
- **Хавфли амал тасдиғи**: `onsubmit="return confirm('${msg.lookup(...)}');"`
 (`src/main/jte/ledger/journalEntryView.jte:129-130`).

## 2а. Combobox (Penguin)

Қидирувли танлагичлар (Arbitr-123 свипи; реф: `docs/penguin-refs/
combobox.html` - «with search» варианти асос; эски `select.combo` +
combobox.js оиласи ўчирилган). Каноник шакл БИТТА жойда - фрагментлар:

```
@template.shared.combobox(name = "vendorId", msg = msg,
 value = form.getVendorId,
 placeholder = msg.lookup("bill.form.selectVendor"),
 required = true,
 cls = "mt-1 w-full",
 addUrl = canEdit != null && canEdit ? "/vendors/quick" : null,
 addLabel = canEdit != null && canEdit ? msg.lookup("combo.addVendor") : null,
 options = @`
 @template.shared.comboOption(value = "", label = msg.lookup("..."))
 @for(...)
 @template.shared.comboOption(value = ..., label = ..., selected = ...)
 @endfor
 `)
```

- **Тузилма** (фрагмент ичида, Penguin «with search» айнан): триггер
 тугма (танланган ёрлиқ + шеврон) + 1px кўринар value input (name -
 эски select name АЙНАН, POST шакли ўзгармас; required validation
 bubble шу input'да) + fixed панел (қидирув инпути + `ul>li`
 рўйхат). Мантиқ `static/js/penguin-combobox.js`
 (`Alpine.data('pgCombo')`) - Penguin'нинг ўз minimal услуби.
- **Вариантлар JTE @for билан** `comboOption` li бўлиб чиқади (карта
 1-қолип, филтр клиентда); гуруҳ сарлавҳаси - `comboGroup`; счёт
 дарахти - `comboAccountOptions` (accountOptions'нинг li твини:
 тур гуруҳлари + NBSP погона + data-cur/data-undep; postable=false
 ота счётлар кўрсатилмайди).
- **Қолип танлови (сурув, журнал 123)**: контакт/товар/счёт/ходим/
 омбор/категория - combobox; валюта/бирлик/солиқ/тўлов шарти/усули/
 тур филтри/classSelect - native select (қисқа рўйхат ёки программ
 интероп: contact-currency/rate-block/UoM филтр/x-model). Сервер
 қидируви бу босқичда ЙЎҚ (эндпойнт йўқ - клиент филтр бугунги оқим
 билан паритет); эндпойнт очилса hxGet/hxTarget параметрлари тайёр.
- **Интероп контракти**: танланган li'нинг data-* атрибутлари value
 input'га кўчади ва ундан change (bubbles) отилади - эски
 `selectedOptions[0].dataset.X` ўқишлар `input.dataset.X` бўлади,
 HTMX (`hxGet`/`hxTarget` → hidden input'даги hx-get) ва Alpine
 занжирлари ишлайверади. Ташқи программ ёзув: `input.value = x;
 input.dispatchEvent(new Event('change', {bubbles:true}))` -
 комбобокс кўринишни ўзи синхронлайди. Ташқи филтрлар (Arbitr-070
 валюта филтри) `li.hidden`ни бошқаради - клиент қидируви
 (style.display) билан тўқнашмайди.
- **Quick-add**: `addUrl`/`addLabel` берилса рўйхат пастида доимий
 «+ Янги ...» банди (филтрга бўйсунмайди, QBO услуби) - мавжуд
 quickForm оқими (GET `<addUrl>-form` → `form[data-combo-quick]` →
 AJAX POST Accept:json → `{id,label[,currency]}`; 422 →
 `[data-combo-error]`), модал қобиғи фрагмент ичида (Penguin modal,
 z-[110]).
- **Зич қатор** (line-item): `dense = true` (py-1.5); `cls` - ўрам
 эни (`w-full min-w-[180px]` ва ҳ.к.); `xShow`/`cloak` - Alpine
 шартли кўриниш (row type); `onchange` - value input'даги
 x-on:change ифодаси; `id` - мавжуд JS ҳуклар учун (payBank,
 rcptDeposit, srBankSel, adjWarehouse, wtrFromWarehouse).
- **Dark/мобил**: dark жуфтлари Penguin рефдан айнан; панел fixed
 (табл/drawer overflow клипидан қочади, z-[100]), пастга сиғмаса
 тепага очилади, эни триггерга тенг (мин 220px, viewport ичида).
 Тегилмайдиган ҳолат: label МАТНИ босилганда дропдаун очилмайди
 (label forwarding қурбон-тугмаси - реф изоҳи).

## 3. Флеш хабарлар

- Controller: `redirect.addFlashAttribute("message"/"error", ...)`;
 хатолар ФАҚАТ `BusinessRuleException.displayMessage` матни билан.
- Экран: сарлавҳадан кейин `@template.shared.alert(message = message,
 error = error)` - Penguin alert жуфтини (success/danger) фрагмент
 чиқаради (4а-бўлим; `src/main/jte/item/units.jte:19`). Фақат биттаси
 керак бўлса фақат ўша параметр берилади.
- Форма саҳифаларида ҳам ФАҚАТ `@template.shared.alert(...)` -
 эски inline яшил/қизил `<p>` хабарлар Arbitr-121 свипида alert'га
 ўтган.
- Форма хато билан қайтганда киритилган қийматлар сақланади: controller
 `fillFormModel(model, form)` билан ўша view'ни қайта беради
 (`src/main/java/com/averpo/erp/purchase/web/BillController.java` - `save`).

## 4. Тугмалар (Penguin)

Ҳамма тугма ва тугма-кўринишли `<a>` **Penguin utility йиғимида**
ёзилади (Arbitr-118 свипи: `.btn` оиласи ўчирилган; реф:
`docs/penguin-refs/buttons.html`). Каноник шакл БИТТА, фақат вариант
токени алмашади:

```
cursor-pointer whitespace-nowrap rounded-radius border border-<V> bg-<V>
px-4 py-2 text-center text-sm font-medium tracking-wide text-on-<V>
transition hover:opacity-75 focus-visible:outline-2
focus-visible:outline-offset-2 focus-visible:outline-<V>
active:opacity-100 active:outline-offset-0
disabled:cursor-not-allowed disabled:opacity-75
dark:border-<V>-dark dark:bg-<V>-dark dark:text-on-<V>-dark
dark:focus-visible:outline-<V>-dark
```

| Вариант (`<V>`) | Қачон | Намуна |
|---|---|---|
| `primary` | Асосий амал: Янги, Филтрлаш, submit | `src/main/jte/purchase/bills.jte` (Янги bill) |
| `success` (+ `font-semibold`, dark:'сиз) | Сақлаш/пост - форма footer, QBO яшил | `src/main/jte/purchase/billForm.jte` (footer) |
| ghost: `bg-transparent border-outline-dark text-on-surface-dark hover:border-outline-dark-strong hover:text-on-surface-dark-strong` | Бекор - ФАҚАТ қора барларда (formfooter/drawer-foot) | `src/main/jte/purchase/billForm.jte` (Бекор) |
| `secondary` | Иккиламчи амал: Таҳрирлаш, Ёпиш, қатор қўшиш | `src/main/jte/purchase/billView.jte` |
| `danger` (dark:'сиз) | Хавфли: delete, деактивация | `src/main/jte/ledger/journalEntryView.jte` |
| `warning` (dark:'сиз) | Огоҳ: reverse/сторно | `shared/reverseForm.jte` |
| `info` (dark:'сиз) | Ажралиб турувчи ёрдамчи амал (payroll prefill) | `src/main/jte/payroll/payrollRunForm.jte` |
| outline: `bg-surface text-primary border-primary hover:bg-primary/10` (+ dark жуфтлари) | Оқ фонли иккиламчи кириш нуқтаси | `src/main/jte/ledger/journalEntries.jte` (Даврни ёпиш) |

Қоидалар:
- `<a>` тегида йиғим бошига `inline-block no-underline` қўшилади
 (тугма-ҳавола матн ҳаволаси канонидаги underline'ни олмасин).
- **Кичик ўлчам** (жадвал қатори ичи): `px-4 py-2 text-sm` ўрнига
 `px-2.5 py-1 text-xs` (`src/main/jte/shared/currencies.jte`).
- Shared ҳолат ранглари (`success`/`danger`/`warning`/`info`) @theme'да
 dark эгизсиз - уларга `dark:*` ёзилмайди.
- Ҳолатга боғлиқ вариант - JTE шартли класс (`class="${шарт ? "..." :
 "..."}"`, намуна: `shared/currencies.jte` актив/нофаол тугмаси).
 Inline `style` билан вариант ясаш ТАҚИҚ.
- ⋮ меню - тугма эмас: Penguin dropdown, `shared/rowMenu.jte`
 (1-бўлим; реф: `docs/penguin-refs/dropdown-menu.html`). Sidebar
 «+ Янги» - success вариант + plus иконка (`layout/sidebar.jte`,
 қидирувдан пастда).

Ҳаволалар: оддий матн ҳаволаси канони `text-primary underline
dark:text-primary-dark`, кўриш саҳифасида «← Рўйхатга қайтиш»
(`src/main/jte/ledger/journalEntryView.jte:21`).

## 4а. Badge / Alert / Card / саҳифа сарлавҳаси / иккиламчи матн (Penguin)

Бу оилалар Penguin utility йиғимларида (Arbitr-119 свипи; эски
`.badge`/`.alert-danger`/`.card`/`.card-grid`/`.muted`/`.muted-sm`/
`.page-header` класслари ўчирилган). Рефлар: `docs/penguin-refs/`
badge.html / alert.html / card.html.

- **Badge (статус чипи)**: ФАҚАТ `@template.shared.badge(...)` орқали -
 чип markup'и ва ранг харитаси фрагментда БИР жойда. Каноник шакл:
 `w-fit whitespace-nowrap rounded-radius border px-2 py-1 text-xs
 font-medium` + тон: success/warning/danger (солид, dark:'сиз) ёки
 нейтрал `border-outline bg-surface-alt text-on-surface` (+ dark
 жуфтлари). Ҳамма оила (paystatus ҳам) бир хил чип кўринишида.
- **Alert (флеш хабар)**: `@template.shared.alert(message, error)` -
 ихчам Penguin alert: ўрам `rounded-radius border border-<V> bg-surface
 text-on-surface` (+ dark жуфтлари), ичида `bg-<V>/10 p-3 text-sm
 font-medium text-<V>` қатлам; `role="alert"`.
- **Card (инфо-карточка)**:
 `rounded-radius border border-outline bg-surface-alt p-4 text-on-surface
 dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark`.
 Кўриш инфо-панели бунга grid қўшади: `mb-4 grid gap-3
 [grid-template-columns:repeat(auto-fit,minmax(180px,1fr))]`
 (намуна: `src/main/jte/sales/invoiceView.jte:48`). Dashboard
 карталари ҳам шу ўрамда (грид minmax 300px).
- **Саҳифа сарлавҳаси**: `<div class="mb-4 flex flex-wrap items-center
 justify-between gap-2">` - `h1` чапда, амал/линк ўнгда, тор экранда
 wrap (намуна: `src/main/jte/purchase/bills.jte:21`).
- **Сарлавҳа тэглари канони (Arbitr-122)** - preflight ўлчам бермайди,
 ҳар сарлавҳага класс ёзилади:
 `h1` → `text-2xl font-bold text-on-surface-strong
 dark:text-on-surface-dark-strong`; `h2` → `text-xl font-bold` (+ ўша
 ранглар); `h3` → `text-lg font-semibold` (+); `h4` → `text-base
 font-semibold` (+). Оралиқ керак бўлса ёнига `mb-*`/`mt-*`.
- **Боди/контент зонаси (Arbitr-122; оқ канвас - Arbitr-123 қўшимча,
 фойдаланувчи қарори)**: `main.jte` (ва `form.jte`) body =
 `bg-surface text-on-surface dark:bg-surface-dark
 dark:text-on-surface-dark` (+ main'да `print:bg-white
 print:text-black`) - light канвас ОҚ, ажралиш бордер/thead/alt
 фонлар орқали; `.main` - услубсиз зона маркери + `min-w-0 flex-1
 p-4 md:px-8 md:py-6 print:p-0`. Тўқ режимда бутун саҳифа тўқ;
 Аралашда контент ОЧ (dark: жуфтлар фақат html.dark'да ёнади -
 app.css variant).
- **Иккиламчи матн** (эски muted): `text-current/60`, кичик ўлчами
 `text-sm text-current/60` - жорий матн рангининг 60%-и (color-mix,
 қўлламайдиган браузерда тўлиқ рангга тушади). currentColor меросдан
 олингани учун ҳар қандай Penguin сиртда dark жуфти автоматик:
 контейнернинг `dark:text-on-surface-dark`'идан 60% олинади. Учинчи
 поғона (жуда хира - муддат/техник изоҳ): `text-current/50`.
- **Матн ҳаволаси канони**: `text-primary underline
 dark:text-primary-dark` (жадвал катаги ичида эса фрагмент
 `[&_td>a]` варианти - 1-бўлим).
- **(?) ёрдам тугмаси**: `helpbtn` - JS delegation МАРКЕРИ
 (helpDialog.jte), кўриниши тугманинг ўзида: майдон даражаси
 `shared/help.jte` (size-5), саҳифа даражаси layout'ларда
 (size-[26px] float-right); иккисида ҳам `border-current/40
 text-current/60 hover:bg-primary hover:text-on-primary` оиласи.
- **Қидирув натижалари** (`search/group.jte`): гуруҳлараро чизиқ
 `[&+&]:border-t` variant'ида; `search-hit`/`search-hit-label` - JS
 маркер класслари (палитра клавиатура навигацияси), услуб ёнидаги
 утилиталарда; танланган сатр `[&.on]:bg-primary/5`.
- **Чоп этиш**: элементда `print:hidden` (экран-фақат) / `hidden
 print:block` (чоп-фақат) / `print:p-0` ва ҳ.к. Глобал print
 нормализацияси (жадвал фонлари, `a` ранги, `@page`) - main.jte
 минимал `<style>` блокида (элементга ёзиб бўлмайдиганлар).

## 5. Кўрсатиш қоидалари (Fmt)

Ҳамма сон экранга ФАҚАТ `shared/web/Fmt.java` орқали чиқади - хом
`BigDecimal` тақиқ. Ўнлик ажратгич вергул, минг ажратгич NBSP.
Тўлиқ қоидалар: `docs/modules/ui-navigation-display.md` B/C қисмлари.

| Helper | Қачон | Мисол чиқиш | Манба |
|---|---|---|---|
| `Fmt.money(Money)` | Ҳужжат суммаси - сумма + валюта коди | `12 600,505 USD` | `Fmt.java:83` |
| `Fmt.money(BigDecimal)` | Валюта контексти аниқ жой (ҳисобот катаги, home устун); қатъий 3 хона | `12 600,505` | `Fmt.java:71`, `MONEY_DISPLAY_SCALE` `:28` |
| `Fmt.qty(qty, unit)` | Миқдор доим бирлиги билан | `10 дона`, `5,5 кг` | `Fmt.java:104` |
| `Fmt.qty(qty)` | Бирлиги маълум бўлмаган миқдор (макс 4 хона, trailing нолсиз) | `5,5` | `Fmt.java:92` |
| `Fmt.rate(rate)` | Валюта курси: >= 1 → қатъий 2 хона; < 1 → макс 8 хона, trailing нолсиз (Arbitr-135) | `12 090.45`, `0.00008334` | `Fmt.java:138` |
| `Fmt.n(value)` | ФАҚАТ форма prefill - хом кўриниш, серверга қайтиб parse бўлади | `2.5` | `Fmt.java:55` |
| `Fmt.dt(instant, zone)` | UTC вақтни компания минтақасида кўрсатиш | ` 21:45` | `Fmt.java:125` |

Қўшимча конвенциялар:
- `BigDecimal` + алоҳида `Currency` entity бўлса код қўлда ёзилади:
 `${Fmt.money(bill.getTotal)} ${bill.getCurrency.getCode}`
 (`src/main/jte/purchase/bills.jte:87-88`).
- Ҳисоботлар тепасида битта ёзув: `report.allAmountsIn` («Барча
 суммалар {0} да») - катакларга код ёзилмайди
 (`src/main/jte/ledger/trialBalance.jte:26`).
- Жадвал устуни бир хил валютада бўлса код сарлавҳага чиқади:
 `(${homeCurrency})` (`src/main/jte/inventory/balances.jte`,
 `src/main/jte/item/list.jte:67`).
- Чет валютали ҳужжат кўринишида валюта + курс қатори:
 `src/main/jte/purchase/billView.jte:56-59`; home эквиваленти tfoot'да
 (`billView.jte:129-135`).

## 6. Мобил (375px) ечимлари

- **Drawer sidebar**: lg'дан пастда мобил бар (бургер,
 `layout/main.jte` header) + sidebar fixed drawer + overlay - Alpine
 `nav` ҳолати; кўриниш тўлиқ Tailwind утилиталарда
 (`layout/sidebar.jte`).
- **Иконка-рейл** (десктоп): sidebar 64px'га йиғилади, группалар
 hover'да flyout (`layout/sidebar.jte`, `in-[.sb-rail]`
 variant'лари), ҳолат `localStorage.sbRail`.
- **iOS zoom олдини олиш**: мобилда `input/select`га `font-size:16px`
 (иккала layout'нинг минимал `<style>` блоки; button рўйхатда йўқ -
 Arbitr-118).
- **Жадваллар**: `shared/table.jte` ўрами (`overflow-x-auto`) +
 `cls = "min-w-[NNNpx]"` (1-бўлим) - экран эмас, жадвал скролл бўлади.
- **Формалар/сарлавҳалар**: `flex-wrap:wrap` - майдонлар тор экранда
 пастга тушади; кўриш карталари `auto-fit,minmax(180px,1fr)` билан
 ўзи йиғилади (2-бўлим).

## 7. Навигация

- **Sidebar группалари**: аккордеон - фақат қўлда очилади/ёпилади,
 ҳолат `localStorage.sbGrps` (`layout/sidebar.jte`, `data-g`
 группалар). Янги модул - янги `data-g` группа шу қолипда.
- **Жорий саҳифа белгиси**: энг узун мос `href` ютади - скрипт фақат
 `aria-current` қўяди, ажралиш sidebar'даги `aria-[current=page]:`
 variant'ларида (`layout/main.jte` пастки скрипти).
- **Row-click (T0)**: қатор `data-href` олади - глобал JS ўтказади
 (`layout/main.jte` T0 скрипти), қатор ичидаги
 `a/button/input/select/label/textarea/[data-rowmenu]` босилганда
 ишламайди; cursor+hover канон фрагмент variant'ларида (1-бўлим).
 Шартли қатор:
 `data-href="${шарт ? манзил : null}"` - JTE null атрибутни бутунлай
 ташлаб юборади (`src/main/jte/item/list.jte:76`). Қайси экран қаерга
 боради - spec T1-T11 жадвали. Мисоллар:
 - ҳужжат рўйхати, DRAFT'га форма: `src/main/jte/purchase/bills.jte:83`
 - ҳисоботдан drill-down (давр филтри билан):
 `src/main/jte/ledger/trialBalance.jte:66`
 - филтрли рўйхатга ўтиш: `src/main/jte/purchase/apAging.jte:62`,
 `src/main/jte/inventory/balances.jte:67`
 - ҳужжатга шартли ўтиш (BILL кирими):
 `src/main/jte/inventory/movements.jte:73`
- **Қатор ичидаги link'лар қолади**: row-click мавжуд Edit/рақам
 ҳаволаларини бекор қилмайди (`src/main/jte/item/list.jte:89`) -
 QBO услуби.
- **Тил алмаштиргич**: sidebar пастида `?lang=uz|ru|en`
 (`layout/sidebar.jte`).

## UI FRAMEWORK (доимий қоида: янги экран фақат Tailwind+Penguin билан)

Йўналиш специ: **docs/modules/ui-framework.md (ТАСДИҚЛАНГАН)** - Tailwind CSS 4.3+ + Penguin UI (компонент
қолипларининг ягона манбаси) + Alpine 3 плагинлар + Tabler Icons,
икки mode (light/dark). ҚОИДА: янги экранда inline style ТАҚИҚ -
фақат Penguin қолиплари; йўқ компонент аввал фойдаланувчи билан
келишилади. Бу гид пилот босқичида «Penguin қолиплар каталоги»
сифатида қайта ёзилади - унгача қуйидаги эски қолиплар фақат
МАВЖУД экранларни ўқишга хизмат қилади (янги экранга андоза ЭМАС).

| Компонент | Чақирув | Нима учун |
|---|---|---|
| pagination | @template.shared.pagination(...) | Рўйхат саҳифалаш (Page<>) |
| listFilter | @template.shared.listFilter(...) | Рўйхат филтр қатори |
| help | @template.shared.help(key, msg) | (?) ёрдам тугмаси |
| docIcon | @template.shared.docIcon(...) | Ҳужжат тури иконаси |
| rowMenu | @template.shared.rowMenu(...) | Қатор амаллар менюси |
| classSelect | @template.shared.classSelect(...) | TxnClass танлагич |
| rateBlock | @template.shared.rateBlock(...) | Валюта+курс блоки (097) |
| attachments | @template.shared.attachments(...) | Иловалар блоки |
| csrf | @template.shared.csrf(csrf = csrf) | ҲАР POST форма бошидаги CSRF hidden input (098) |
| badge | @template.shared.badge(value = st.name, msg = msg) | Статус чипи (Penguin, 4а-бўлим); ранг харитаси фрагмент ичида БИР жойда; family параметри - i18n оиласи: status (default) / paystatus / est.status / po.status / rcn.status |
| alert | @template.shared.alert(message = message, error = error) | Флеш хабар жуфти (Penguin success/danger, 4а-бўлим), сарлавҳадан кейин |
| reverseForm | @template.shared.reverseForm(action, confirmKey, today, csrf, msg [, labelPrefix]) | Сторно формаси (сана+сабаб+confirm); labelPrefix - label калит префикси, default "bt.view" (098) |
| jeLink | @template.shared.jeLink(sourceType, id, msg) | Ҳужжат view'идан GL (JE) га ҳавола; POSTED/REVERSED шартини чақирувчи текширади (098) |
| money-input.js | class="money" | Пул киритиш (формат+калькулятор) |
| combobox | @template.shared.combobox(...) | Қидирувли танлагич (+Янги) - 2а-бўлим; мантиқ penguin-combobox.js (pgCombo) |
| comboOption / comboGroup / comboAccountOptions | @template.shared.comboOption(...) | Combobox бандлари: оддий / гуруҳ сарлавҳаси / счёт дарахти |
| rate-block.js | data-rate-block | Курс prefill/кўрсатиш |

### UI-LIB утилита класслари ва семантик ранглар (098, main.jte style блоки)

Семантик ранг ўзгарувчилари: `--ok` #15803d (яшил), `--danger` #b91c1c
(қизил), `--warn` #b45309 (сариқ), `--muted` #64748b (кулранг матн),
`--muted2` #94a3b8 (очроқ кулранг). Экранда хом hex ёзилмайди - var
ишлатилади. Улар фақат ҚОЛГАН эски inline услублар ва search/menu
бўлимлари учун туради; muted/badge/alert МАТН класслари Penguin'да
(4а-бўлим).

| Класс | Қиймати | Қачон |
|---|---|---|
| матн ҳаволаси | class="text-primary underline dark:text-primary-dark" | Оддий матн ҳаволаси («Орқага», drill-down) - жадвалдан ташқарида (4а-бўлим) |
| погона | class="pl-4!" / "pl-7!" | Ҳисобот дарахти погоналари (important - канон катак паддингидан устун) |

Жадвал атомиклари (.r/.c/.num) Arbitr-120 свипида Tailwind
утилиталарига ўтган (1-бўлим канони). Badge / alert / card / саҳифа
сарлавҳаси / иккиламчи матн - Penguin utility йиғимларида, 4а-бўлимга
қаранг (Arbitr-119).

Форма контроллари фрагмент эмас - тўғридан-тўғри Penguin utility
йиғимларида ёзилади (Arbitr-121 қарори, 2-бўлим канони; эски
.form-control/.fgrid режалари бекор - Тугмалар (118) услуби танланди).

## Четлашишлар (Arbitr-007/008 номзодлари - бу ерда ФАҚАТ қайд)

1. ~~Такрор локал zebra~~ - Arbitr-120 свипида ҳал: zebra фақат
 shared/table.jte канонида, локал `<style>.zebra` блоклари ва
 `class="zebra"` ўчирилган.
2. **Import'сиз Fmt**: `ledger/accounts.jte:110` - Fmt тўлиқ package
 йўли билан чақирилган, бошқа ҳамма экранда `@import` бор.
3. **`inv.form.unitCost` матни ғализ**: калитнинг ўзида қавсли изоҳ бор
 («... (пусто - текущая стоимость)»), ёнига U4 валюта спани қўшилгач
 (`inventory/adjustmentForm.jte:76`) ўқилиши оғирлашди - калит матнини
 соддалаштириш керак.
4. **T3 тўлиқ эмас**: spec бўйича journalEntries'да DRAFT қатор формага
 бориши керак, лекин JE'da draft таҳрир формаси/route умуман йўқ -
 ҳозирча DRAFT ҳам view'га боради
 (`ledger/journalEntries.jte:71-74` изоҳида қайд этилган). Backend
 (PostingService draft update) кутилмоқда - Ғайрат ҳудуди.
5. **Сарлавҳа h1 ораликлари бир хил эмас**: кўпчилик экранда `mb-4`,
 report'ларда `mb-1.5` (остида изоҳ қатори бор) - қоида сифатида
 шуни танладик: изоҳ қатори бўлса `mb-1.5`, бўлмаса `mb-4`.
