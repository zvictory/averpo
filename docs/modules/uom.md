# UoM - Бирлик гуруҳлари ва конверсия (task #37) - SPEC

## Мақсад
Ўлчов бирликларини гуруҳлаш ва бир гуруҳ ичида конверсия: «қути =
12 дона»да ҳужжатга қутида киритиб, омборга донада тушириш. QBO
Online'да UoM ЙЎҚ - бу multi-warehouse inventory кенгайтмамизнинг
рухсат этилган қисми (Unit каталоги каби, engineering-rules.md эталон банди).
Ғоя манбаси: docs/old-erp-ideas.md §9 (соддалаштириш қуйида).

## Қатъий қарорлар (тасдиқланган)
- **Кўлам тўлиқ**: каталог + Bill/Invoice ITEM сатрларида бирлик
 танлаш. Омбор қолдиғи, каталог нархлар, valuation - ҲАМИША item
 base бирлигида, уларга тегилмайди.
- **Factor бирликнинг ўзида** (эски ERP'даги жуфт uom_conversion
 жадвали ОЛИНМАДИ): гуруҳдаги ҳар бирликда base'га нисбатан битта
 factor (кг=1 base, гр=0.001, тонна=1000). Исталган жуфт конверсия
 base орқали - зиддият бўлиши мумкин эмас. Гуруҳлараро конверсия тақиқ.
- **Item**: мавжуд unit = base бирлик + иккита ихтиёрий default:
 харид бирлиги (bill формада prefill) ва сотув бирлиги (invoice) -
 иккиси base билан бир гуруҳдан.
- **Ҳужжат сатрида snapshot**: миқдор/нарх киритилган бирликда
 сақланади + ўша пайтдаги factor (Money.exchangeRate услуби) - кейин
 каталогда factor ўзгарса тарихий ҳужжат бузилмайди. Омборга
 base миқдор = qty × factor (scale 4, HALF_UP).
- **GL ўзгармайди**: сумма = миқдор × нарх (киритилган бирликда),
 posting-rules формулалари ўша-ўша. Янги ҳужжат тури йўқ.

## Модел (changeset 026)

### UnitGroup (unit_group) - янги
| Майдон | Тип | Изоҳ |
|---|---|---|
| name | varchar(50) unique | «Оғирлик», «Дона ҳисоби»... |

### Unit (unit) - кенгайтма
| Майдон | Тип | Изоҳ |
|---|---|---|
| group_id | UUID nullable (FK unit_group) | Гуруҳсиз бирлик - конверсиясиз (ҳозиргилар шундай қолади) |
| factor | numeric(24,12) NOT NULL default 1, CHECK > 0 | 1 шу бирлик = factor × base |
| is_base | boolean default false | Гуруҳда айнан битта: partial unique (group_id) WHERE is_base |

### Item (item) - кенгайтма
| Майдон | Тип | Изоҳ |
|---|---|---|
| purchase_unit_id | UUID nullable (FK unit) | Bill сатрида default бирлик; null - base |
| sales_unit_id | UUID nullable (FK unit) | Invoice сатрида default бирлик; null - base |

### BillLine / InvoiceLine - кенгайтма (3-4-турткилар, changeset'и ўшанда)
| Майдон | Тип | Изоҳ |
|---|---|---|
| unit_id | UUID nullable (FK unit) | Киритилган бирлик; null - item base (эски ёзувлар) |
| unit_factor | numeric(24,12) nullable | SNAPSHOT: сатр ёзилган пайтдаги factor; null - 1 |

Base миқдор сатрда сақланмайди - qty × unit_factor (scale 4 HALF_UP)
ҳисобланади ва айнан шу қиймат омбор ҳаракатига ёзилади (movement'да
бор). Reverse омбор ҳаракати миқдоридан юради - қўшимча ҳеч нарса шарт
эмас.

## Хизмат қатлами
UnitService кенгаяди (item модули):
- Гуруҳ CRUD: `groups`, `createGroup(name)`, `renameGroup(id, name)`.
 Гуруҳ ўчирилмайди (бирликлар тарихи), нофаоллаштириш ҳам йўқ -
 бирликларнинг ўзи нофаолланади.
- `create/update` бирликда group/factor/isBase билан - инвариантлар
 қуйида. Эски имзолар сақланади (гуруҳсиз бирлик).
- Конверсия ёрдамчилари (3-4-турткилар ишлатади):
 `toBase(unit, qty)` = qty × factor;
 `factorBetween(from, to)` = from.factor / to.factor (бир гуруҳ шарт);
 `selectableUnits(baseUnit)` - ҳужжат сатри select'и учун: base
 гуруҳидаги фаоллар (гуруҳсиз base'да фақат ўзи).

## Валидация (BR-UOM + BR-ITM-012)
| Код | Қоида |
|---|---|
| BR-UOM-001 | Гуруҳ номи бўш эмас ва unique |
| BR-UOM-002 | Factor > 0 (DB CHECK ҳам) |
| BR-UOM-003 | Base бирликнинг factor'и айнан 1 |
| BR-UOM-004 | Гуруҳда айнан битта base: гуруҳга биринчи кирган бирлик base бўлади; гуруҳда бошқа бирликлар бор экан base мақомини йўқотмайди/гуруҳдан чиқмайди (DB partial unique ҳам) |
| BR-UOM-005 | Гуруҳсиз бирликка factor/base қўйилмайди (factor 1) |
| BR-UOM-006 | Конверсия фақат бир гуруҳ ичида |
| BR-ITM-012 | Item default харид/сотув бирлиги item base бирлиги билан бир гуруҳдан (base гуруҳсиз ёки танланмаган бўлса default қўйиб бўлмайди) |

Изоҳ: factor'ни КЕЙИН ўзгартириш эркин - тарихий ҳужжатлар snapshot
сақлайди, омбор ҳисоби base'да бўлгани учун ҳеч нарса «сузмайди».

## Экранлар
- 2-туртки: /settings/units - гуруҳлар блоки (қўшиш/номлаш) + бирлик
 формасида гуруҳ/factor/base; гуруҳ устунлари рўйхатда. Item
 формасида default харид/сотув бирлиги select'лари (base гуруҳига
 қараб Alpine билан филтрланади).
- 3-туртки: bill формасида ITEM сатрида бирлик select (data-factor,
 нарх prefill = base нарх × factor), кўриш экранида бирлик.
- 4-туртки: invoice формасида худди шундай.

## Туртки режаси (ҳар бири алоҳида тасдиқланади)
1. Spec (шу ҳужжат) + changeset 026 + UnitGroup/Unit/Item domain +
 BR каталог + UnitService кенгайтмаси + ItemService BR-ITM-012 +
 тестлар.
2. UI: /settings/units гуруҳ/factor/base билан, Item формасида
 default бирликлар.
3. Bill интеграцияси: BillLine.unit_id/unit_factor (changeset 027),
 форма select + prefill, post'да омборга base миқдор, тестлар.
4. Invoice интеграцияси: InvoiceLine кенгайтмаси, форма, issue/COGS
 base миқдорда, тестлар + roadmap ✅.

## Тестлар (мажбурий рўйхат)
- Гуруҳ: биринчи бирлик base (factor 1 мажбурий), иккинчиси factor
 билан; BR-UOM-002 (factor ≤ 0), 003 (base factor != 1), 004 (иккинчи
 base рад / base гуруҳдан чиқиши рад), 005 (гуруҳсизга factor).
- Конверсия: toBase, factorBetween (тўғри/тескари), гуруҳлараро рад
 (BR-UOM-006); selectableUnits base гуруҳини қайтаради.
- Item: default бирликлар бир гуруҳдан (BR-ITM-012 - бошқа гуруҳ рад,
 гуруҳсиз base'га default рад), эски 12 майдонли ItemData
 чақирувлари ишлайверади.
- 3-4-турткиларда: қутида кирим → омборга дона × factor; нарх prefill;
 reverse аниқ қайтади; эски (unit'сиз) сатрлар factor 1 деб ўқилади.
