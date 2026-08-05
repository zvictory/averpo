# Detail type номларини QBO расмий номларига тузатиш - РЕЖА

Асос: entities.md §1.2 - 6 та enum константа номи расмий
`AccountSubTypeEnum` (Finance.xsd) билан мос эмас, 2 таси расмий
enum'да умуман йўқ. Мақсад: DB'даги string'лар ва Java enum расмий
CamelCase номларнинг SNAKE_CASE кўринишига 1:1 мос бўлсин.

Ҳолат: ✅ БАЖАРИЛДИ (2026-07-06, changeset 018-detail-type-rename).
Хавфлар бўлимидаги «маълум бага» текширилди: AccountService.importCsv
аллақачон valueOf IAE'сини BR-COA-004 га ўрайди, currency хатоси ҳам
BR-COA-004 контексти билан қайта ўралади - тузатиш талаб қилинмади.

## Ном мослиги (қарорлар)

| Эски (бизда) | Янги (QBO расмий) | Изоҳ |
|---|---|---|
| SUPPLIES_AND_MATERIALS_COGS | SUPPLIES_MATERIALS_COGS | `SuppliesMaterialsCogs` |
| SUPPLIES | SUPPLIES_MATERIALS | `SuppliesMaterials` |
| OFFICE_EXPENSES | OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES | `OfficeGeneralAdministrativeExpenses` |
| PAID_IN_CAPITAL | PAID_IN_CAPITAL_OR_SURPLUS | `PaidInCapitalOrSurplus` |
| TRUST_ACCOUNT | TRUST_ACCOUNTS | `TrustAccounts` |
| PAYROLL_LIABILITIES | PAYROLL_CLEARING | Расмийда `PayrollLiabilities` йўқ; мавжуд «Иш ҳақи мажбуриятлари» счёти семантикасига `PayrollClearing` мос. Қўшимча: `PAYROLL_TAX_PAYABLE` ҳам enum'га ЯНГИ тур сифатида киритилади (расмийда бор) |
| UNEARNED_REVENUE | OTHER_CURRENT_LIABILITIES | Расмийда йўқ (US тўпламида ҳам). «Олинган аванслар» счёти номи ўзгармайди, фақат detail type умумий турга ўтади - QBO ҳам deferred revenue учун шуни ишлатади |

`INVENTORY_CLEARING` ТЕГИЛМАЙДИ - ҳужжатланган landed cost
кенгайтмаси (multi-warehouse'нинг қисми).

## Таъсирланадиган жойлар (2026-07-06 grep билан аниқланган)

1. **AccountDetailType.java** - 7 константа rename + 1 янги
   (PAYROLL_TAX_PAYABLE, OTHER_CURRENT_LIABILITY турига). JavaDoc
   изоҳлари янгиланади.
2. **ItemService.java** - 2 жой: default expense счёт қидируви
   (`AccountDetailType.SUPPLIES_AND_MATERIALS_COGS`) + JavaDoc.
3. **default-chart.csv** - 5 қатор: Иш ҳақи мажбуриятлари, Олинган
   аванслар, Товар таннархи (COGS), Офис харажатлари, Сарф
   материаллари.
4. **messages.properties / _en / _ru** - `account.detail.*` калитлари:
   7 та rename × 3 файл + 1 янги калит × 3 файл (PAYROLL_TAX_PAYABLE
   учун титул: «Иш ҳақи солиқлари» / Payroll Tax Payable / Налоги с
   зарплаты).
5. **Liquibase data changeset 018** (янги файл
   `018-detail-type-rename.sql`): ҳар мослик учун
   `UPDATE account SET detail_type='ЯНГИ' WHERE detail_type='ЭСКИ';`
   (7 та UPDATE). Мавжуд dev/prod базадаги қаторлар шу орқали ўтади;
   `type`/`classification` устунлари ЎЗГАРМАЙДИ (мослик жадвалида тур
   сақланиб қолади, фақат PAYROLL_LIABILITIES ва UNEARNED_REVENUE'да
   ҳам тур OTHER_CURRENT_LIABILITY лигича қолади - текширилди).
6. **docs/posting-rules.md** - 2 жой (SUPPLIES_AND_MATERIALS_COGS
   тизим счёти жадвали + Invoice COGS қоидаси).
7. **docs/modules/item.md** - 1 жой (default expense).
8. **docs/qbo-reference/entities.md §1.2** - жадвал «бажарилди» деб
   янгиланади.

Тестларда эски номлар ишлатилмаган (grep тоза). Changelog'ларда seed
йўқ (счётлар runtime'да CSV'дан импорт қилинади).

## Бажариш тартиби (битта сессия, ~10 файл)

1. Changeset 018 ёзилади (Liquibase аввал data'ни ўтказади - илова
   кўтарилганда enum valueOf эски string учратмайди).
2. Enum + ItemService + CSV + messages бир туртки'да.
3. Ҳужжатлар (posting-rules, item.md, entities.md) иккинчи туртки'да.
4. Текширув: `grep -rn "SUPPLIES_AND_MATERIALS_COGS\|OFFICE_EXPENSES\|PAID_IN_CAPITAL\b\|TRUST_ACCOUNT\b\|PAYROLL_LIABILITIES\|UNEARNED_REVENUE"`
   бутун repo бўйича 0 натижа қайтариши шарт (қавс: PAID_IN_CAPITAL_OR_SURPLUS
   ичидаги prefix'ни ҳисобга олиб \b билан).
5. `./gradlew test` тўлиқ ўтади; қўлда smoke: /accounts рўйхати ва
   счёт формаси dropdown титуллари тўғри чиқади.

## Хавфлар

- Фойдаланувчининг ЭСКИ CSV файллари (эски detail type номлари билан)
  импортда ишламай қолади - бу қабул қилинади (хато хабари аниқ
  чиқади). Alias-мослаштириш атайлаб қилинмайди - soddalik.
- AccountImportTest'даги маълум бага (importCsv'да `catch
  (BusinessRuleException)` ўрнига `IllegalArgumentException` тутилиши
  керак, AccountService.java:267) шу сессияда бирга тузатилиши мақсадга
  мувофиқ - нотўғри detail type киритилганда фойдаланувчи тартибли
  BR-COA-004 хабарини кўради.
