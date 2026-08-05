# Combobox: қидирувли select + жойида «Янги қўшиш» - SPEC

## Мақсад

Каталогдан танланадиган ҲАР БИР select (счёт, мижоз, таъминотчи,
омбор, товар, бирлик, солиқ, валюта...) учун ягона component:
(1) рўйхат узун бўлса ёзиб ҚИДИРИШ; (2) керакли ёзув йўқ бўлса ўша
жойнинг ўзида «+ Янги қўшиш» - формадан чиқиб кетмасдан. Фойдаланувчи
талаби. Эталон: QBO'нинг барча каталог dropdown'лари
айнан шундай (type-to-search + «+ Add new» биринчи қатор).

## Кўлам (1-босқич - MVP)

**Қидирув-филтр - ҳамма каталог select'ига** (`class="combo"` белгиси
билан). Арбитр съёмкаси бўйича қамров:

| Гуруҳ | Майдонлар (JTE'даги name) |
|---|---|
| Контакт | customerId, vendorId, contactId, lines[].contactId, lines[].employeeId |
| Счёт | bankAccountId, lines[].accountId, accountId, depositAccountId, incomeAccountId, expenseAccountId, inventoryAssetAccountId, parentId (счёт формаси) |
| Товар | lines[].itemId, itemId |
| Омбор | warehouseId, lines[].warehouseId, fromWarehouseId, toWarehouseId |
| Бошқа каталоглар | lines[].unitId, unitId, salesUnitId, purchaseUnitId, lines[].taxRateId, salesTaxRateId, purchaseTaxRateId, currency, paymentTermId, paymentMethodId, categoryId |

Enum/тизим select'лари (status, type, role, timezone ва ҳ.к.) combo
ОЛМАЙДИ - қисқа рўйхатлар, native select қолади.

**«+ Янги қўшиш» - ФАҚАТ тўрттасида** (service create минимал
талаблари текширилган):

| Entity | Quick-add майдонлари | Service |
|---|---|---|
| Мижоз | ном (displayName) | ContactService.create(CUSTOMER, ...) |
| Таъминотчи | ном | ContactService.create(VENDOR, ...) |
| Счёт | ном + detail type (группаланган select) | AccountService.create |
| Омбор | ном + код | WarehouseService.create |

Товар quick-add MVP'га КИРМАЙДИ (unit + income/expense счётлар талаб
қилинади - модал катталашиб кетади, BR'лари кўп) - 2-босқич.
Ходим ҳам кирмайди (payroll майдонлари оғир - contact-card прецеденти).

## Дизайн қарорлари

- **Progressive enhancement**: native `<select class="combo">` ДОМ'да
  қолади (яширинади), component қиймати ўша select'га синхронланади -
  форма submit, controller'лар, web тестлар ЎЗГАРМАЙДИ. JS ўчиқ
  бўлса native select ишлайверади.
- Битта янги файл: `static/js/combobox.js` (money-input.js услубида
  ўз-ўзини улайдиган, vanilla/Alpine-дўст). Vendor'га ТАШҚИ
  БИБЛИОТЕКА ҚЎШИЛМАЙДИ (alpine/htmx кифоя - стек қоидаси).
- Қидирув: клиент томонда, render қилинган option'лар устидан
  (AJAX ЙЎҚ - рўйхатлар ҳозирги model'лардан келаверади); катта-кичик
  ҳарф фарқсиз, label'нинг исталган жойидан мос келади. Клавиатура:
  ↑/↓/Enter/Esc (глобал қидирув dropdown нақши - DEC-039).
- Динамик сатрлар (Alpine x-for қўшадиган line row'лар): янги
  қўшилган сатрдаги select ҳам enhance қилиниши ШАРТ - delegation/
  MutationObserver ёки row-add hook (coder танлайди, талаб spec'да).
- **Add-new оқими**: select'да `data-add-url` бўлса рўйхат тепасида
  «+ Янги мижоз...» қатори. Босилганда HTMX модал: GET fragment
  (мини форма, CSRF token билан) → POST quick endpoint → жавобда
  {id, label} → JS янги option қўшиб ТАНЛАЙДИ, модал ёпилади -
  ота-форма ҳолати бузилмайди. Хато (BR) - модал ичида
  displayMessage кўрсатилади.
- Quick endpoint'лар юпқа, ҳар модул ЎЗИНИКИни беради (қоида 6):
  POST /customers/quick, /vendors/quick, /accounts/quick,
  /warehouses/quick (+ GET .../quick-form fragment'лари). Мавжуд
  service'лар қайта ишлатилади - янги бизнес мантиқ ЙЎҚ.
- **Роллар**: data-add-url server томонда роль текшируви билан
  render қилинади - VIEWER_AUDITOR'га add-new КЎРИНМАЙДИ (endpoint'да ҳам
  SecurityConfig ёзув қоидаси амал қилади).
- Мобил 375px: dropdown тўлиқ кенгликда, max-height + scroll, touch
  учун қатор баландлиги етарли.
- Changeset КЕРАК ЭМАС. Янги матнлар i18n уч тилда.

## Тестлар (мажбурий рўйхат)

1. Quick endpoint'лар web тести: муваффақиятли create (id+label
   қайтади), BR хато оқими, VIEWER_AUDITOR'га 403.
2. Fragment формалар render тести (CSRF token бор).
3. Мавжуд ScreenSmoke/web тестлар яшиллигича (native select
   сақлангани исботи).
4. ЖОНЛИ smoke МАЖБУРИЙ (JS'ни gradle тестламайди): invoice формада
   товар қидируви; шу форманинг ўзидан янги мижоз қўшиб танланиши;
   375px кўриниш - скриншотлар билан.

## Экранлар

Янги саҳифа ЙЎҚ - мавжуд формалардаги select'лар кучаяди. Модал
fragment'лар: contact/account/warehouse quick формалари.

## 2-босқич (ҳозир ЭМАС)

- Товар quick-add (unit + счётлар билан), ходим quick-add.
- Солиқ/бирлик/валюта/тўлов шарти quick-add.
- Жуда катта рўйхатлар учун server-side AJAX қидирув.
- «Охирги ишлатилганлар» рўйхат тепасида.
