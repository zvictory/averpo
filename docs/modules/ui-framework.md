# UI Framework - Tailwind 4 + Penguin UI + Alpine 3

Ҳолат: **ТАСДИҚЛАНГАН (фойдаланувчи, 2026-07-16)**. Бу ҳужжат UI
йўналишининг ягона манбаси; аввалги ui-lib йўналиши бекор қилинган.

## Қарор ва сабаб

Фойдаланувчи қарори (2026-07-16): **тайёр, QA'дан ўтган
кутубхоналар ишлатилади - генерик UI компонентларни ўзимиз
қурмаймиз.** Сабаб: ўзимиз қўлда қурганлар ҳар хил code-style ва
UI/UX услубга бўлиниб кетди. Ўз UI-LIB қуриш дастури ва уни
опенсорс қилиш нияти БЕКОР.

## Стек (барчаси энг охирги, ўзаро мос версиялар)

| Қатлам | Танлов | Изоҳ |
|---|---|---|
| CSS | **Tailwind CSS 4.3+** (июн 2026 да охиргиси 4.3.2) | standalone CLI (Node шарт эмас), CSS-first конфиг (`@theme`) |
| Компонент қолиплари | **Penguin UI** | ЯГОНА манба: copy-paste HTML (Tailwind+Alpine), алоҳида JS кутубхонаси йўқ |
| Интерактив | **Alpine.js 3** (охирги 3.x) + расмий плагинлар | Mask, Collapse, Focus, Persist; кейинга: Anchor, Sort |
| Иконка | **Tabler Icons** | фақат SVG иконкалар (MIT), inline/спрайт |
| Шрифт | **Inter** (сарлавҳа + матн) | локал woff2, **кирилл subset МАЖБУРИЙ** (UI кирилл ўзбекчада) |
| Серверга уланиш | **HTMX** (мавжуд) | ўзгармайди |

Аниқ версиялар инфра картасида PIN қилинади («энг охирги, ўзаро
мос» тамойили - фойдаланувчи 2026-07-16); ижро пайтида Context7
орқали қайта текширилади.

ҚЎШИЛМАЙДИ: daisyUI (Penguin билан вазифа тўқнашуви - иккита тема
тизими ва дизайн тили бўлиб қоларди), Bootstrap ва бошқа оғир
framework'лар, chart кутубхонаси (dashboard SVG графиги қолади).

**Ҳамма ресурс ЛОКАЛ** (jar ичида): Tailwind чиқарган CSS, Alpine ва
плагин файллари, Tabler SVG'лар. CDN ишлатилмайди - тизим offline
муҳитда ҳам ишлайди.

## Build

Tailwind standalone CLI `src/main/jte/**/*.jte` (ва керакли .java)
файллардан класс йиғиб битта минифи CSS чиқаради. Gradle'да:
- `processResources` олдидан exec task (build/CI учун);
- dev режимда watch (ихтиёрий, ёки қайта run).
Бу лойиҳанинг ЯГОНА frontend build қадами - JS build йўқ.

### Build амалда (Arbitr-116)

- PIN: Tailwind CLI **v4.3.2** standalone (SHA256'лар build.gradle'да,
  манба - релизнинг расмий sha256sums.txt файли).
- `./gradlew tailwindBuild`: binary биринчи прогонда юкланиб
  `.gradle/tailwind/v4.3.2/` га кэшланади (репога кирмайди), кейин
  `src/main/css/app.css` дан минифи CSS
  `build/generated/tailwind/static/css/app.css` га йиғилади.
  `processResources` шу task'ка боғланган - `build`/`bootRun`/`test`
  да автоматик юради; натижа jar ичида `/css/app.css` бўлиб хизмат
  қилади (`src/` га generated файл ёзилмайди).
- Dev оқими: JTE'да класс ўзгарса оддий қайта run кифоя (bootRun
  олдидан processResources қайта йиғади). Узлуксиз watch керак бўлса
  кэшдаги binary қўлда юргизилади: `.gradle/tailwind/v4.3.2/
  tailwindcss-windows-x64.exe -i src/main/css/app.css -o
  build/generated/tailwind/static/css/app.css --watch`.
- Вендоринг PIN'лари: Alpine.js ядро + плагинлар (mask/collapse/
  focus/persist) **3.15.12** - `static/js/vendor/` (ҳар файл бошида
  версия изоҳи); Inter woff2 subset'лар (**@fontsource/inter 5.2.8**:
  latin, latin-ext, cyrillic, cyrillic-ext; 400/500/600/700) -
  `static/fonts/`; Tabler Icons **3.44.0** намуна фрагменти -
  `src/main/jte/icons/settings.jte`.
- ДИҚҚАТ: `app.css` ҳали бирорта саҳифага уланмаган (preflight глобал
  reset беради) - улаш 117 скелет картасида.

## Тема ва ранглар

Фойдаланувчи танлаган Penguin палитраси АЙНАН (semantic токенлар):

```css
@theme {
    /* Light Theme */
    --color-surface: var(--color-white);
    --color-surface-alt: var(--color-slate-100);
    --color-on-surface: var(--color-slate-700);
    --color-on-surface-strong: var(--color-black);
    --color-primary: var(--color-blue-700);
    --color-on-primary: var(--color-slate-100);
    --color-secondary: var(--color-indigo-700);
    --color-on-secondary: var(--color-slate-100);
    --color-outline: var(--color-slate-300);
    --color-outline-strong: var(--color-slate-800);

    /* Dark Theme */
    --color-surface-dark: var(--color-slate-900);
    --color-surface-dark-alt: var(--color-slate-800);
    --color-on-surface-dark: var(--color-slate-300);
    --color-on-surface-dark-strong: var(--color-white);
    --color-primary-dark: var(--color-blue-600);
    --color-on-primary-dark: var(--color-slate-100);
    --color-secondary-dark: var(--color-indigo-600);
    --color-on-secondary-dark: var(--color-slate-100);
    --color-outline-dark: var(--color-slate-700);
    --color-outline-dark-strong: var(--color-slate-300);

    /* Shared Colors */
    --color-info: var(--color-sky-600);
    --color-on-info: var(--color-white);
    --color-success: var(--color-green-600);
    --color-on-success: var(--color-white);
    --color-warning: var(--color-amber-500);
    --color-on-warning: var(--color-white);
    --color-danger: var(--color-red-600);
    --color-on-danger: var(--color-white);

    /* Border Radius */
    --radius-radius: var(--radius-md);
}
```

**Уч режим (фойдаланувчи 2026-07-17, Arbitr-117в)**: Оч / Тўқ /
Аралаш. Аралашда фақат sidebar оиласи (nav дарахти + мобил бар) тўқ,
контент оч. Механизм: `<html>`'да `dark` (тўлиқ тун) ёки `sb-dark`
(аралаш) класси; app.css `@custom-variant dark` қамрови sb-dark
остида `#sbnav`/`.sb-zone` дарахти билан кенгайтирилган - dark:
жуфтлари ўзгаришсиз ишлайди. `.sb-light` - тескари истисно: sidebar
ичида туриб контентга эргашадиган subtree («+ Янги» панели -
фойдаланувчи 2026-07-17: Аралашда панел оч; тўлиқ Тўқда тўқ). Танлов user-profile попапида (3 ҳолатли
segmented), Persist `uiMode` калити (эски boolean `darkMode`дан
автоматик кўчиш; эски калит синхрон ёзилади - form layout FOUC'и
учун). Ҳар янги компонент камида Оч ва Тўқ режимларда текширилади.

**Тема манбаси**: Penguin theme конфигуратори - **arctic** темаси,
Inter/Inter шрифтлар, radius `-md`, юқоридаги ранглар (фойдаланувчи
танлови 2026-07-16, конфигуратор URL'и билан тасдиқланган).

## Компонентлар қоидаси

- **Ҳар қандай UI бўлаги учун аввал Penguin'дан қолип олинади**:
  жадвал, input/форма, card, badge, alert/toast, modal, dropdown,
  tabs, **пагинация** - ҳаммаси. Ўзимиз янги генерик компонент
  ўйлаб топмаймиз; Penguin'да йўқ нарса чиқса - аввал фойдаланувчи
  билан келишилади.
- Такрорланадиган қолиплар умумий **JTE фрагмент**ларга олинади
  (жадвал устма-уст ёзилмасин) - лекин фрагмент ичи соф
  Penguin/Tailwind markup.
- Inline style тақиқи ҚОЛАДИ.
- ui-style-guide.md пилот босқичида «Penguin қолиплар каталоги»
  сифатида қайта ёзилади (қайси компонент қаердан олинган, қандай
  токенлар билан).

## Пул ва сонлар (темир чегара)

- Киритишда **Alpine Mask** (`$money()`): минг ажратгич NBSP, каср
  НУҚТА; пул 2 хона, миқдор макс 4, курс макс 6 - Fmt қоидаларига
  айнан мос параметрланади. Мавжуд қўлбола money-input/edit-buffer
  кодининг ўрнини босади.
- Mask - ФАҚАТ киритиш қулайлиги. Сақлаш/ҳисоб аниқлиги серверда:
  `Money`, `FormParsers` (вергулни ҳам қабул қилаверади - ҳимоя
  қатлами), кўрсатиш `Fmt` орқали - engineering-rules.md 1-темир қоида
  ўзгармайди.

## Sidebar (скелет босқичининг маркази)

Penguin «Sidebar with collapsible menus» қолипи асосида, уч зона:

1. **Тепа**: white-label бренд (112 сақланади: компания логоси ёки
   «Averpo ERP»).
2. **Ўрта**: навигация гуруҳлари (Сотув, Харид, Омбор, Банк,
   Ҳисобот, Payroll, Созламалар...) - Collapse билан очил-ёпил,
   жорий экран гуруҳи автоматик очиқ, фаол пункт ажралган; қайси
   гуруҳлар очиқлиги Persist'да (сервер render'ида ҳолат
   йўқолмайди). Пункт кўриниши серверда роль шартлари билан
   (hasAuthority) - мантиқ ўзгармайди.
3. **Паст**: **user-profile item** - аватар-инициал, исм, роль;
   меню: Профиль, Компания маълумотлари, Чиқиш (POST + CSRF).

Режимлар:
- **Desktop**: тўлиқ очиқ ҲАМДА **иконкагача йиғиладиган режим**
  (фақат иконкалар қолади, hover/фокусда tooltip ном кўрсатади;
  ҳолат Persist'да).
- **Мобил (<lg)**: drawer - overlay, Focus плагини фокусни тутади,
  Escape ёпади. 375px қоидаси сақланади.

Иконкалар: ҳар пунктга Tabler Icons SVG.

**Қидирув - command-palette (фойдаланувчи 2026-07-17)**: sidebar'да
триггер (лупа + `Ctrl + K` ёрлиғи), босилганда ёки Ctrl+K'да
марказда modal (Penguin қидируви услуби): автофокус майдон, гуруҳли
натижалар, ↑/↓ + Enter навигация, Esc ёпади. Орқа фон ДЕЯРЛИ ШАФФОФ
(кучли blur ERP'га мос эмас - орқадаги экран танилиб туриши шарт).
Фақат main.jte layout'ида. Карта: Arbitr-117в.

**Топбар ЙЎҚ (фойдаланувчи 2026-07-16)**: desktop'да топбар
бўлмайди - контент тепадан бошланади; мобилда фақат минимал бар
(hamburger + бренд). «+ Янги» sidebar'да (қидирувдан пастда,
success/яшил вариант - 2026-07-17), dark toggle user-profile
попапида.

## Миграция режаси (тизим тўхтамайди)

| Босқич | Мазмун | Ҳолат |
|---|---|---|
| 0. Инфра | Tailwind CLI + Gradle + @theme + Alpine плагинлар + Tabler SVG'лар вендоринги | ✅ 2026-07-16 (Arbitr-116) |
| 1. Скелет | layout + Penguin sidebar + топбар + login + dark toggle | ✅ 2026-07-16 (Arbitr-117) |
| 2+. КОМПОНЕНТ свиплари | Қуйида - фойдаланувчи қарори 2026-07-16 | навбатда |

**Компонент свиплари** (модул тўлқинлари ўрнига - фойдаланувчи
2026-07-16: ўз CSS энг тез йўқолсин, ҳар свип ўлчанадиган): ҳар
свип БИТТА компонент оиласини БУТУН лойиҳада Penguin'га алмаштиради
ва main.jte/form.jte style блокидан ўз бўлимини ЎЧИРАДИ. Мезон:
54 ўз селектор → 0.

1. Тугмалар (.btn оиласи) - Arbitr-118;
2. Badge/card/alert/muted - Arbitr-119;
3. Жадвал (zebra, actions, data-href, .table-wrap) + пагинация -
   Arbitr-120;
4. Форма элементлари (input/label/.cols2/каталог drawer) -
   Arbitr-121;
5. Қолган утилиталар (.r/.c/.num/.mb1...) → Tailwind утилиталари +
   preflight компенсация блокини ўчириш - Arbitr-122.

Ҳар свипда: ui-style-guide.md'га шу компонентнинг Penguin бўлими
ёзилади (каталог шу тарзда қайта туғилади); такрор оғир жойда
умумий JTE фрагмент мумкин (ичи соф Penguin utility - янги CSS
класс ТАҚИҚ). Ўтиш даврида икки услуб ёнма-ён - вақтинчалик
номувофиқлик қабул қилинган.

Ҳар кўчирилган экран мезонлари: иккала mode'да тўғри, 375px да
ишлайди, жадваллар overflow-x контейнерда, helpKey сақланган,
уч тил texti бузилмаган.

## Билим манбалари

- Context7 MCP уланган (.mcp.json) - Tailwind 4 / Alpine 3 нинг энг
  янги ҳужжатлари сессия ичида олинади.
- Penguin компонентлари: ишлатилганлари реф сифатида репога
  кўчирилади (вендоринг) - сайтга боғланиб қолмаймиз.
