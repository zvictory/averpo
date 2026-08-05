# PriceList - Нарх рўйхатлари (task #38) - SPEC

## Мақсад
Мижоз тоифаларига алоҳида сотув нархлари: нарх рўйхати каталоги,
поғонали (volume) нархлар, мижоз бириктируви ва invoice формасида
автоматик нарх prefill. Ғоя манбаси: docs/old-erp-ideas.md §10
(price_list + price_list_item); QBO мослиги: Price rules (қоида
кимларга тегишли - бириктирув рўйхат томонида). DiscountRule
(фоиз/фикс чегирма, priority, combinable) АТАЙЛАБ КЕЙИНГИ БОСҚИЧГА
қолдирилди (қарори).

## Қатъий қарорлар (тасдиқланган)
- **Фақат PriceList** - DiscountRule кейин; чегирма ҳозирча нархни
  қўлда ўзгартириш билан ҳал қилинади.
- **Поғонали нарх**: (item, min_quantity) → нарх. Миқдорга қараб энг
  катта мос поғона олинади (1+ → 10 000, 100+ → 9 000 каби).
- **Мижоз бириктируви РЎЙХАТ томонида** (price_list_customer, мижозга
  биттагина рўйхат): QBO Price rules услуби, contact модулига
  тегилмайди - contact ↔ pricing доиравий боғлиқлик олдини олинади
  (pricing → contact, тескариси йўқ).
- **Фақат PREFILL**: ечилган нарх invoice сатрига таклиф сифатида
  қўйилади, қўлда ўзгартириш эркин; ҳужжатга рўйхат ҳаваласи
  САҚЛАНМАЙДИ (QBO услуби) - GL/posting'га умуман таъсир йўқ.
- **Нарх item BASE бирлигига** (UoM конвенцияси): формада танланган
  бирлик factor'и prefill устига кўпайтирилади (мавжуд JS оқими).
- **Битта default рўйхат** (partial unique, old-erp): мижозга рўйхат
  бириктирилмаган бўлса default ишлатилади. Янги default белгиланса
  эскиси автоматик бўшатилади (contact default манзил прецеденти).
- Рўйхат **валютаси** ҳужжат валютасига мос бўлгандагина қўлланади;
  давр (valid_from/to) ҳам текширилади - мос келмаса навбатдаги
  номзод (default), у ҳам бўлмаса каталог нархи (item.salesPrice).

## Модел (changeset 031, янги `pricing` модули)

### PriceList (price_list)
| Майдон | Тип | Изоҳ |
|---|---|---|
| name | varchar(100) unique | «Улгуржи», «VIP мижозлар»... |
| currency_id | UUID FK currency NOT NULL | Рўйхат нархлари шу валютада |
| valid_from / valid_to | date nullable | Амал даври (иккиси ҳам ихтиёрий) |
| is_default | boolean default false | Partial unique (is_default) WHERE is_default |
| active | boolean default true | Нофаол рўйхат ечишда қатнашмайди |

### PriceListItem (price_list_item)
| Майдон | Тип | Изоҳ |
|---|---|---|
| price_list_id | UUID FK | |
| item_id | UUID FK item | dimension эмас - DB FK бор, JPA'да UUID (қоида №6) |
| min_quantity | numeric(19,4) > 0, default 1 | Поғона бошланиши, BASE бирликда |
| price | numeric(19,4) >= 0 | Рўйхат валютасида, base бирликка |

UNIQUE(price_list_id, item_id, min_quantity) - BR-PL-005.

### PriceListCustomer (price_list_customer)
| Майдон | Тип | Изоҳ |
|---|---|---|
| price_list_id | UUID FK | |
| customer_id | UUID FK contact, **UNIQUE глобал** | Мижозга биттагина рўйхат (BR-PL-006); бошқа рўйхатга бириктирилса автоматик кўчади |

Барча жадвалларда BaseEntity устунлари (id, version, created_at,
updated_at, created_by).

## Ечиш тартиби - PriceListService.resolvePrice (public API)
`resolvePrice(customerId, itemId, baseQty, currencyCode, date)
→ Optional<BigDecimal>`:

1. Номзодлар тартиби: мижоз рўйхати (бириктирув бўйича) → default
   рўйхат (иккиси бир хил бўлса бир марта).
2. Ҳар номзодда «қўлланади» текшируви: active + валюта коди мос +
   date ∈ [valid_from, valid_to] (null чегара - чексиз).
3. Қўлланадиган рўйхатда item поғоналари: min_quantity <= baseQty
   бўлганлардан ЭНГ КАТТАСИ → нархи қайтади.
4. Рўйхат қўлланмаса ёки item поғонаси топилмаса - навбатдаги номзод;
   ҳеч бирида йўқ - empty (чақирувчи каталог нархга қайтади).
5. baseQty null/нол - 1 деб олинади (форма ҳали миқдор киритмаган).

customerId null (мижоз танланмаган) - фақат default рўйхат кўрилади.

## Валидация (BR-PL)
| Код | Қоида |
|---|---|
| BR-PL-001 | Рўйхат номи бўш эмас ва unique |
| BR-PL-002 | Нарх манфий эмас, min_quantity мусбат сон |
| BR-PL-003 | Default рўйхат биттагина (янгиси белгиланса эскиси автоматик бўшатилади; DB partial unique пойга ҳимояси) |
| BR-PL-004 | valid_from valid_to дан кейин бўлмаслиги шарт |
| BR-PL-005 | (рўйхат, item, min_quantity) поғонаси такрорланмайди (409, DB unique ҳам) |
| BR-PL-006 | Мижозга биттагина рўйхат (бошқасига бириктирилса кўчади; DB unique пойга ҳимояси, 409) |
| BR-PL-007 | Поғона фақат мавжуд ва фаол item учун киритилади (тури аҳамиятсиз - хизматга ҳам нарх рўйхати бўлади) |
| BR-PL-008 | Бириктирув фақат CUSTOMER типдаги фаол контакт учун |

Рўйхат валютаси CurrencyService.require орқали (BR-CUR-* кодлари).
Ўчириш йўқ - active=false (каталог қоидаси); поғона қаторлари эса
оддий ўчирилади (ҳужжатларга ҳавола йўқ - фақат prefill манбаси).

## Экранлар
- 2-туртки: /settings/price-lists (ADMIN) - рўйхатлар жадвали +
  карта саҳифаси (unitGroups услуби): сарлавҳа (ном/валюта/давр/
  default/фаол), поғоналар жадвали («100+ дона → 9 000» ўқилиши),
  поғона қўшиш формаси (item select + min_qty + нарх), мижоз
  бириктириш блоки (select + рўйхатдагилар). Settings каталог
  ҳаволаларига қўшилади.
- 3-туртки: invoice формасида prefill - `GET /price-lists/lookup?
  customerId&itemId&qty&currency&date` (exchange-rates/lookup
  паттерни, plain text нарх ёки бўш). Форма JS: мижоз/item/миқдор/
  бирлик ўзгарганда чақиради, натижа × unit factor нарх майдонига
  (қўлда киритилган нарх устидан фақат item/бирлик алмашганда
  ёзилади - мавжуд UoM prefill семантикаси сақланади).

## Туртки режаси (ҳар бири алоҳида тасдиқланади)
1. Spec (шу ҳужжат) + changeset 031 + pricing domain/repo/service
   (CRUD, поғоналар, бириктирув, resolvePrice) + BR-PL каталог/enum +
   тўлиқ тестлар.
2. /settings/price-lists UI (рўйхат + карта) + settings ҳаволаси +
   ScreenSmokeTest.
3. Invoice prefill (lookup endpoint + форма JS) + жонли текширув +
  roadmap .

## Тестлар (мажбурий рўйхат)
- Каталог: ном unique (BR-PL-001), сана тартиби (BR-PL-004), default
  алмашуви (эскиси бўшайди, иккитаси бўлмайди), валюта require.
- Поғоналар: min_quantity/нарх валидацияси (BR-PL-002), дубликат
  поғона (BR-PL-005), нофаол item рад (BR-PL-007).
- Бириктирув: CUSTOMER бўлмаган контакт рад (BR-PL-008), мижоз бошқа
  рўйхатга бириктирилса кўчади (эскисидан ўчади).
- resolvePrice: поғона танлаш (qty 1 → 10 000, qty 150 → 9 000);
  валюта мос эмас → default'га ўтади; сана давр ташқарисида → default;
  мижоз рўйхатида item йўқ → default'дагиси; ҳеч қаерда йўқ → empty;
  customerId null → фақат default; нофаол рўйхат ўтказиб юборилади.
