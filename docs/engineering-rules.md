# Муҳандислик қоидалари (темир қоидалар)

Averpo ERP - битта бизнеснинг молиясини тўлиқ бошқарадиган тизим:
бухгалтерия ядроси, сотув, харид, кўп-омборли инвентар, банк,
ҳисоботлар, landed cost. Multi-currency: home currency
CompanySettings'да сақланади (default UZS), биринчи POSTED
проводкадан кейин қулфланади. Счётлар режаси - IFRS услубида.

Бу ҳужжат лойиҳанинг ўзгармас муҳандислик шартномаси: қуйидаги
қоидалар ҳеч қачон бузилмайди ва ҳар code-review шуларга қарши
текширилади.

## Домен моделини текшириш

Дизайн қарорлари бўшлиқда қабул қилинмайди. Домен тузилмаси - счётлар
режаси (Classification → AccountType → DetailType), home currency,
ҳужжат оқимлари, ҳисоботлар - халқаро амалиётга қарши текширилади:
`docs/qbo-reference/` да расмий Finance.xsd схемаси ва майдонма-майдон
солиштирув сақланади. Икки ечим орасида иккиланилса - аввал схема
текширилади, веб-саҳифа эмас (схема - бирламчи манба).

Мақсад - **домен тўлиқлиги**: муҳим бирор нарса тушиб қолмаслиги ва
атамаларнинг соҳада қабул қилинганига мос келиши.

**Атайлаб кўпроқ қилинган ИККИ соҳа:**
1. **Кўп-омборли инвентар** (SME сегментидаги булут маҳсулотларида
   омбор тушунчаси йўқ); landed cost ва Unit каталоги шу кенгайтманинг
   таркибий қисмлари.
2. **Payroll Lite** (халқаро SME маҳсулотларида ойлик алоҳида сотилади -
   бу ерда эса битта тизимда кутилади).

Кўламни жиловлаш онгли: янги имконият рақобатчида бор деб эмас,
маҳаллий бизнес эҳтиёжи исботлангач қўшилади.

## Стек

- Java 21, Spring Boot 4, Gradle
- UI: JTE + HTMX + Alpine.js 3 (SPA framework ЙЎҚ); Tailwind CSS 4 +
  Penguin UI қолиплари + Tabler Icons, икки режим (оч/тўқ); ҳамма
  ресурс локал (CDN йўқ)
- Hibernate (Spring Data JPA), PostgreSQL 18, Liquibase
- Тест: JUnit 5, реал PostgreSQL'даги алоҳида тест базаси
- Хавфсизлик: Spring Security form login + CSRF, 8 роллик модель
  (рухсат рольга эмас СОҲАга - hasAuthority permission)
- ID: UUIDv7 (`shared.Uuid7`), Hibernate генератори эмас - қўлда тайинланади

## Темир қоидалар

1. Пул = `shared.Money` embeddable. Ҳеч қачон double/float эмас.
   amount (ҳужжат валютаси) + baseAmount (home валютада) + exchangeRate.
2. GL'га ёзиш ФАҚАТ `ledger.service.PostingService` орқали. Бошқа
   модуллар JournalEntry'ни тўғридан-тўғри яратмайди/сақламайди.
3. POSTED ҳужжат ўзгартирилмайди - фақат reverse (сторно).
   Update/delete тақиқ.
4. Ledger home валютада балансланади: `sum(debitBase) == sum(creditBase)`.
5. Ҳар бир schema ўзгариши = янги Liquibase changeset
   (`db/changelog/` ичида, кетма-кет рақам билан). `ddl-auto=validate`.
6. Модуллараро мурожаат фақат public service интерфейс орқали. Бошқа
   модулнинг repository'сига тегиш тақиқ. `ledger` ҳеч кимга боғлиқ эмас.
7. Ҳар бир posting логикага unit test - `debit == credit` assert қилинади.
8. Проводкалар `docs/posting-rules.md` га қатъий мос бўлиши шарт. Янги
   ҳужжат тури = аввал posting-rules.md га қоида қўшиш.
9. Inventory valuation: AVCO ёки FIFO - CompanySettings'да компания
   даражасида танланади, биринчи омбор ҳаракатидан кейин қулфланади.
   Иккаласи ҳам (item, warehouse) даражасида ҳисобланади.
10. ҲАР БИР field ва method изоҳли бўлиши ШАРТ (JavaDoc). Изоҳсиз код
    merge қилинмайди. Изоҳ «нима»ни эмас, «нега»ни ёзади.
11. Валюта - алоҳида `Currency` domain entity (каталог). `Money` ичида
    ISO код сақланади (denormalized), FK эмас.
12. Барча вақтлар базада UTC (timestamptz). Экранда CompanySettings
    timezone'ида кўрсатилади.
13. Бизнес қоида бузилиши = `BusinessRuleException` (ноёб BR-* код
    билан, каталог: `docs/business-rules.md`). Янги қоида - аввал
    каталогга, кейин кодга. Service'ларда IllegalArgument/IllegalState ТАҚИҚ.

## Код услуби

- Package-by-module: `com.averpo.erp.<module>.{domain,service,repo,web}`
- Lombok: `@Getter`/`@Setter`/`@RequiredArgsConstructor`/
  `@NoArgsConstructor(PROTECTED)`. Entity'да `@Data`/`@EqualsAndHashCode`/
  `@ToString` ТАҚИҚ (BaseEntity'даги identity-based equals бузилади).
  DTO - record. Бизнес-мантиқли конструктор/метод қўлда ёзилади.
- UI mobile-first: ҳар янги экран 375px кенгликда ҳам ишлаши шарт.
  Сайдбар мобилда drawer, жадваллар `.table-wrap` (overflow-x:auto) ичида.
- Пул экранда доим валютаси билан (ҳужжат валютаси коди; ҳисоботларда
  сарлавҳада «Барча суммалар <home> да»), миқдор доим бирлиги билан.
- Яхлитлаш фақат КЎРСАТИШда (`Fmt` орқали): пул қатъий 2 хона, миқдор
  trailing нолсиз макс 4, курс >= 1 да қатъий 2 хона / < 1 да trailing
  нолсиз макс 8. Каср ажратгичи ҳамма жойда НУҚТА (12 600.50), минг
  ажратгичи NBSP. Киритишда вергул ҳам қабул қилинади (`FormParsers`).
  Сақланадиган қийматлар тўлиқ аниқликда.
- Entity'лар `BaseEntity`дан мерос олади (UUID id, version, audit)
- Controller'лар юпқа: validation + service чақируви + view render
- JTE шаблонлар: `src/main/jte/<module>/`, layout:
  `src/main/jte/layout/main.jte`
- HTMX: жадвал/форма қисмларини partial render қилиш учун

## Ишчи тартиб

- Янги feature'дан олдин `docs/modules/` даги спецни ўқи.
- Спец йўқ бўлса - аввал спец ёз (`docs/modules/<name>.md`), тасдиқлат,
  кейин код.
- Архитектура қарорлари: `docs/architecture.md`
- Ҳар сессия охирида тўлиқ тест прогони ўтиши шарт.
