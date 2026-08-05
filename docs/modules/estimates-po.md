# Estimate ва PurchaseOrder (GL'сиз ҳужжатлар) - SPEC

## Мақсад

Иккита non-posting ҳужжат (DEC-017 2-босқич, фойдаланувчи
тасдиқлаган): **Estimate** (мижозга таклиф/смета, QBO Estimate) ва
**PurchaseOrder** (таъминотчига буюртма, QBO PurchaseOrder). Иккиси
ҳам GL'га ЁЗМАЙДИ, омборга тегмайди - фақат ҳужжат оқими ва кейин
Invoice/Bill'га айлантириш. Битта умумий қолип - шунинг учун битта
spec, битта туртки.

Эталон: Finance.xsd Estimate (TxnStatus: Pending/Accepted/Closed/
Rejected) ва PurchaseOrder (POStatus: Open/Closed); иккисида ҳам
LinkedTxn (қайси invoice/bill'га айлангани).

## Умумий дизайн (иккисига)

- Тузилиши invoice/bill КЎЗГУСИ: сарлавҳа (контакт, сана, амал
  муддати (expiration/ship date - ихтиёрий), валюта, курс, memo) +
  сатрлар (item/хизмат, миқдор, бирлик (UoM), нарх, ҚҚС ставкаси) -
  мавжуд сатр қолиплари ва tax.md жонли жами механизми айнан
  (мижоз/таъминотчи тўлиқ суммани кўриши учун ҚҚС ҳисобланади,
  лекин SALES_TAX_PAYABLE'га ҲЕЧ НАРСА ёзилмайди - GL йўқ).
- **POSTED тушунчаси ЙЎҚ** - булар GL'сиз ҳужжатлар, темир қоида 3
  тегишли эмас: ТАҲРИРЛАШ МУМКИН (QBO ҳам шундай), ҳолат status
  билан бошқарилади. Ўчириш ҳам мумкин (фақат айлантирилмаган
  бўлса - BR қуйида). Бу лойиҳада биринчи таҳрирланадиган ҳужжат
  тури - spec'нинг онгли қарори.
- Ҳужжат рақамлари: EST-YYYY-NNNNN, PO-YYYY-NNNNN (DocumentType
  кенгаяди).
- posting-rules.md'да қатори бор: «Estimate / PurchaseOrder - GL
  проводка ЙЎҚ (non-posting)».

## Ҳолатлар (status)

- **Estimate**: PENDING (default) → ACCEPTED ёки REJECTED (қўлда,
  мижоз жавоби) → CLOSED (invoice'га айлантирилганда автоматик,
  ёки қўлда). REJECTED/CLOSED'дан айлантириб бўлмайди.
- **PurchaseOrder**: OPEN (default) → CLOSED (bill'га
  айлантирилганда автоматик, ёки қўлда - бекор бўлган буюртма).

## Айлантириш (conversion) - асосий қиймат

- Estimate → **Invoice**: «Invoice'га айлантириш» тугмаси - invoice
  формаси estimate сатрлари билан PREFILL очилади (мижоз, валюта,
  сатрлар, ҚҚС ставкалари; сана/курс - бугунги). Фойдаланувчи кўриб
  сақлайди - одатдаги invoice POST оқими (GL/омбор ўша ерда).
  Сақлангач estimate CLOSED + linked invoice id сақланади (кўришда
  ҳавола).
- PurchaseOrder → **Bill**: худди шу нақш (bill формаси prefill,
  сақлангач PO CLOSED + linked bill id).
- Қисман айлантириш (сатр танлаш) - 2-БОСҚИЧ; MVP'да тўлиқ ҳужжат
  prefill (фойдаланувчи формада қўлда қисқартириши мумкин - бу ҳам
  амалда қисман қоплайди).

## Entity'лар (changeset - кетма-кет рақам)

`estimate` + `estimate_line`; `purchase_order` + `purchase_order_line`
- invoice/bill жадвал қолипида (BaseEntity, contact_id, doc сана,
ихтиёрий амал муддати, валюта/курс - валюта контактдан олинади
(Contact.currency), ҳужжатда ўзгартирилмайди (QBO қатъий, DEC-087,
BR-EST-004/BR-PO-004; тафсилот multi-currency.md), memo, status,
жами net/tax/gross;
сатрда item_id, qty, unit_id, price, tax_rate_id). Linked ҳужжат:
estimate.invoice_id NULL FK, purchase_order.bill_id NULL FK
(айлантирилгач тўлдирилади). GL/StockMovement жадвалларига АЛОҚА ЙЎҚ.

## Service API (модуллар: sales, purchase)

- `EstimateService`: create/update/delete (айлантирилмаган бўлса),
  changeStatus (PENDING↔ACCEPTED/REJECTED, CLOSED қўлда),
  markConverted(estimateId, invoiceId) - InvoiceController prefill
  оқимидан чақирилади.
- `PurchaseOrderService`: кўзгу (bill билан).
- GL'га мурожаат УМУМАН йўқ - PostingService import қилинмайди
  (review'да текшириладиган нуқта).

## Валидация ва инвариантлар (BR-EST/BR-PO - аввал каталогга)

- BR-EST-001 / BR-PO-001: камида битта сатр, миқдор/нарх мусбат.
- BR-EST-002 / BR-PO-002: CLOSED/REJECTED ҳужжат таҳрирланмайди ва
  айлантирилмайди (аввал қайта очиш - фақат CLOSED→OPEN/PENDING,
  агар linked ҳужжати йўқ бўлса).
- BR-EST-003 / BR-PO-003: айлантирилган (linked) ҳужжат ўчирилмайди
  ва қайта айлантирилмайди.
- BR-EST-004 / BR-PO-004: ҳужжат валютаси контакт валютасига мос
  бўлиши шарт (Contact.currency, null = home; QBO қатъий,
  DEC-087) - server валютани контактдан ЎЗИ олади.
- ҚҚС ҳисоби tax.md механизмида, лекин GL'га ёзилмайди (жонли жами
  фақат кўрсатиш).

## Тестлар (мажбурий рўйхат)

1. Create/update/delete оқими - GL'да ҲЕЧ ҚАНДАЙ JE пайдо
   бўлмаслиги (journal_entry сони ўзгармайди - муҳим assert).
2. Status ўтишлари: PENDING→ACCEPTED→CLOSED; REJECTED'дан
   айлантириш рад (BR-EST-002).
3. Айлантириш: prefill маълумоти тўғри (сатрлар/валюта/ставкалар),
   сақлангач estimate CLOSED + invoice_id тўлган; linked'ни ўчириш
   рад (BR-EST-003).
4. PO → Bill кўзгу тестлари.
5. ScreenSmokeTest: /estimates, /purchase-orders (рўйхат/форма/кўриш).

## Экранлар (JTE routes)

- `/estimates`, `/purchase-orders` - рўйхат (status устуни/филтри) +
  янги/таҳрир (FULL транзакция формаси - invoice/bill қолипи, жонли
  жами) + кўриш (status амаллари + «Айлантириш» тугмаси + linked
  ҳужжат ҳаволаси).
- Invoice кўришида «Estimate'дан» белгиси (linked бўлса) - кўзгуси
  bill'да.
- «+ Янги» менюга иккита банд (Сотув: Estimate; Харид: Буюртма) -
  калитлар аввал grep; сайдбар тегишли гуруҳларга.
- 375px мобил, .table-wrap.

## 2-босқич (ҳозир ЭМАС)

- Қисман айлантириш (сатр танлаш, қолдиқ кузатуви - QBO POStatus
  сатр даражасида);
- Estimate'ни мижозга PDF/email юбориш (печат формалари билан);
- PO'да омборга кутилаётган кирим кўрсаткичи (expected qty).
