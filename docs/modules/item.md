# Item модули - SPEC (3-босқич)

Эталон: QBO Products and Services.

## Мақсад
Товар ва хизматлар каталоги. Inventory (5-босқич) қолдиқ/AVCO'ни,
Sales/Purchases ҳужжат сатрларини шу каталогга боғлайди
(JournalEntryLine.itemId dimension).

## Дизайн қарорлари
1. **Тип** - QBO'дагидек: INVENTORY (омборда сақланадиган товар),
 NON_INVENTORY (сақланмайдиган товар), SERVICE (хизмат).
 Bundle - кейинги босқичлар.
2. **Счёт боғлашлар** - QBO'дагидек ҳар item ўз даромад/харажат/актив
 счётига ишора қилади. Модуллараро қоида (№6) бузилмаслиги учун
 Account'га JPA relation ЭМАС, UUID сақланади; мавжудлик ledger'нинг
 public AccountService'и орқали текширилади (dimension паттерни).
3. **Unit (ўлчов бирлиги)** - QBO'да UoM йўқ, лекин бизнинг
 multi-warehouse inventory кенгайтмамиз (рухсат этилган фарқ)
 бирликсиз ишламайди: qty ҳамиша бирликка боғлиқ. Шунинг учун кичик
 Unit каталоги киритилади ва бу фарқ шу ерда ҳужжатланади.
4. **Бошланғич қолдиқ (initial qty on hand)** - QBO item формасида
 сўрайди; бизда бу 5-босқич (Inventory opening) иши - item формасида
 ЙЎҚ, кейин omborga кирим ҳужжати билан киритилади.
5. **Категория** - QBO услубида иерархик (parent nullable), CoA
 tree view паттерни қайта ишлатилади.

## Entity'лар

### Unit (unit)
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | PK (UUIDv7) |
| name | varchar(50), unique | «дона», «кг», «литр», «соат» |
| active | boolean | |

Seed (changeset): дона, кг, литр, метр, соат, хизмат.

### ItemCategory (item_category)
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | PK |
| name | varchar(255), unique | |
| parent_id | UUID nullable | Иерархия (cycle ҳимояси Account'дагидек) |
| active | boolean | |

### Item (item)
| Майдон | Тип | Изоҳ |
|---|---|---|
| id | UUID | PK (UUIDv7) |
| type | enum ItemType | INVENTORY, NON_INVENTORY, SERVICE |
| name | varchar(255), unique | QBO услуби - асосий идентификатор |
| sku | varchar(50) nullable, unique (агар берилса) | Partial unique index (account.code паттерни) |
| category_id | UUID nullable | ItemCategory'га FK |
| unit_id | UUID nullable | Unit'га FK |
| sales_price | NUMERIC(19,4) nullable | Default сотув нархи (home валютада) |
| sales_description | text nullable | Invoice сатрига default тавсиф |
| income_account_id | UUID | Даромад счёти (ledger Account id, JPA эмас) |
| purchase_cost | NUMERIC(19,4) nullable | Default харид нархи |
| purchase_description | text nullable | Bill сатрига default тавсиф |
| expense_account_id | UUID | INVENTORY учун COGS счёти, бошқаларга харажат счёти |
| inventory_asset_account_id | UUID nullable | Фақат INVENTORY типда шарт |
| reorder_point | NUMERIC(19,4) nullable | Кам қолдиқ огоҳлантириши (кейин) |
| active | boolean | QBO make inactive |

Default счётлар (форма олдиндан тўлдиради, фойдаланувчи ўзгартира
олади) - detail type орқали топилади (posting-rules.md паттерни):
- income: SALES_OF_PRODUCT_INCOME (SERVICE учун SERVICE_FEE_INCOME)
- expense: SUPPLIES_MATERIALS_COGS (INVENTORY),
 бошқаларга OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES эмас -
 фойдаланувчи танлайди, default OTHER_MISCELLANEOUS_SERVICE_COST
- inventory asset: INVENTORY

## Service - public API
```java
public class ItemService {
 Item create(...); Item update(UUID id, ...); Item get(UUID id);
 List<Item> list(ItemType type, boolean includeInactive); // type null - ҳаммаси
}
public class ItemCategoryService { CRUD + tree } // Account tree паттерни
public class UnitService { list/create/rename/toggle }
```

## Валидация
1. name unique; sku берилса unique.
2. INVENTORY: inventory_asset_account_id шарт ва счёт detail type'и
 INVENTORY бўлиши шарт; expense счёти COGS туркумидан бўлиши тавсия
 (қатъий эмас - QBO ҳам мажбурламайди).
3. Барча счёт id'лари мавжуд, active, postable - ledger AccountService
 орқали.
4. sales_price/purchase_cost берилса >= 0.
5. Категория цикли тақиқ (Account'даги requireNoCycle паттерни).
6. Item ўчирилмайди - active тогл (тарихдаги ҳужжатлар бузилмасин).

## Экранлар (JTE, i18n, QBO услуби)
- /items - рўйхат: Ном, SKU, Тип, Категория, Бирлик, Сотув нархи,
 Ҳолат. Тип бўйича филтр. Nofaollar тогли.
- Item формаси - full-screen layout/form.jte: тип танланганда тегишли
 бўлимлар кўринади/яширинади (Alpine): Sales бўлими, Purchasing
 бўлими, Inventory бўлими (фақат INVENTORY).
- /item-categories - оддий рўйхат + форма (иерархия indent билан).
- Units - созламалар ичида кичик жадвал (/settings/units).

## Тестлар (мажбурий)
- item create: default счётлар detail type орқали тўғри топилади.
- INVENTORY тип inventory_asset счётсиз → хато.
- name/sku dup → хато.
- Категория цикли → хато.
- Нофаол счёт билан item create → хато.

## Кейинги босқичларга мослик
- 5-босқич: StockBalance (item, warehouse) - фақат INVENTORY типлар.
- 6/7-босқич: Bill/Invoice сатри item танлаганда нарх/тавсиф/счётлар
 default'дан келади (QBO услуби).
