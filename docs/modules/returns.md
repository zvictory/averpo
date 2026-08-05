# Қайтариш ҳужжатлари (Returns) - SPEC

## Мақсад

Тижорий қайтаришнинг учта QBO ҳужжати (Arbitr-017, фойдаланувчи
тасдиқлаган минимал пакет): **CreditMemo** (мижоз кредит-нотаси),
**RefundReceipt** (мижозга пул қайтариш), **VendorCredit**
(таъминотчи кредит-нотаси). Асл ҳужжатлар: Invoice -
docs/modules/sales.md, Bill - docs/modules/purchases.md; омбор
қайтими - docs/modules/inventory.md. Сторно (reverse) хатони бекор қилади;
булар эса тижорий воқеа: қисман қайтариш, бошқа сана/курс, омборга
қайтган товар, кейин ҳужжатга қўллаш.

Эталон: Finance.xsd CreditMemo (10497), RefundReceipt (10537),
VendorCredit (8723). Проводкалар: docs/posting-rules.md «Қайтариш
(Returns)» бўлими (аввал ёзилди - қоида №8).

## Умумий дизайн (учаласига)

- Тузилиши invoice/bill КЎЗГУСИ: сарлавҳа (контакт, сана, валюта,
 курс, memo) + сатрлар (item/хизмат, миқдор, бирлик (UoM),
 нарх, ҚҚС ставкаси snapshot) - мавжуд сатр қолиплари қайта
 ишлатилади (tax.md net/gross/inclusive механизми айнан).
- DRAFT йўқ - яратилди = POSTED (bank txn нақши); тузатиш reverse
 орқали (POSTED ўзгармас - темир қоида 3).
- Ҳужжат рақамлари: CM-YYYY-NNNNN, RR-YYYY-NNNNN, VC-YYYY-NNNNN
 (DocumentType кенгаяди, DocumentSequenceService).
- Ихтиёрий **асл ҳужжат ҳаволаси** (CreditMemo → invoice,
 VendorCredit → bill): танланса сатрлар prefill бўлади (QBO'да
 ҳам кредит одатда invoice'дан очилади) ва inventory қайтим
 таннархи асл ҳаракатдан олинади.
- Транзакция формаси FULL layout (фойдаланувчи қарори), 375px.

## Entity'лар (changeset - кетма-кет рақам)

`credit_memo` + `credit_memo_line`; `vendor_credit` +
`vendor_credit_line`; `refund_receipt` + `refund_receipt_line` -
учаласи invoice/bill жадвал қолипида (BaseEntity, contact_id,
doc сана/валюта/курс - валюта контактдан олинади (Contact.currency),
ҳужжатда ўзгартирилмайди (QBO қатъий, Arbitr-087, BR-RET-008;
тафсилот multi-currency.md), memo, status POSTED/REVERSED,
jami/net/tax;
сатрда item_id/expense счёти, qty, unit_id, price, tax_rate_id +
snapshot, warehouse_id - inventory сатрда шарт).

Қўллаш (allocation): `credit_application` (credit_memo_id,
invoice_id, amount) ва `vendor_credit_application`
(vendor_credit_id, bill_id, amount) - мавжуд payment allocation
қолипида; кредитнинг очиқ қолдиғи = jami − қўлланган.

ОНГЛИ ФАРҚ (Otabek-009): QBO'да Preferences
`AutoApplyCredit` (Finance.xsd :12301) default'да кредитни очиқ
invoice'ларга АВТОМАТИК қўллайди; бизда apply ФАҚАТ ҚЎЛДА -
бухгалтер қайси ҳужжатга қанча қўлланишини ўзи белгилайди
(аниқ назорат, кичик бизнес учун хатоси кам йўл). AutoApplyCredit
preference - roadmap 1.1 ғоялар рўйхатида.

## Service API (модуллар: sales, purchase)

- `CreditMemoService.create(data)` → POSTED + JE + StockMovement IN;
 `apply(creditMemoId, invoiceId, amount)` - BR-RET текширувлари,
 invoice balance камаяди, FX фарқи бўлса алоҳида JE;
 `reverse(id, date, reason)` - қўлланмаган бўлсагина (BR-RET-007).
- `RefundReceiptService.create(data)` → POSTED + JE + StockMovement
 IN. Application йўқ. `reverse(...)`.
- `VendorCreditService.create(data)` → POSTED + JE + StockMovement
 OUT; `apply(vendorCreditId, billId, amount)`; `reverse(...)`.
- GL фақат PostingService орқали (қоида 2); inventory ҳаракатлари
 inventory public API орқали (қоида 6).

## Posting

docs/posting-rules.md «Қайтариш (Returns)» бўлими - ШУ ҲУЖЖАТ
ЭТАЛОН. Қисқача: CreditMemo - Dr даромад(net) + Dr ҚҚС / Cr AR
(gross) + Dr INVENTORY / Cr COGS (қайтим таннархи); RefundReceipt -
AR ўрнига Cr пул счёти; VendorCredit - Dr AP (gross) / Cr харажат
(net) + Cr ҚҚС + Cr INVENTORY (сиёсат таннархи) + фарқ
OTHER_COSTS_OF_SERVICE_COS. Application GL'сиз (фақат FX фарқи
алоҳида JE). Ҳар posting'да debit == credit (қоида 7 тести).

Application FX JE санаси = ҚЎЛЛАШ куни (компания timezone бугуни),
кредит/ҳужжат санаси ЭМАС (Arbitr-050): realized FX қўллаш пайтида
тан олинади (BillPayment payment_date прецеденти); шу боис эски
(ёпилган) даврдаги кредитни янги очиқ даврдаги invoice/bill'га
қўллаш BR-LED-020 ёпиқ давр блокига урилмайди. Unapply/reverse
сторноси reversalDate билан (одатий сторно нақши - ўзи очиқ давр).

## Inventory қайтим таннархи

- Кирим (CreditMemo/RefundReceipt): асл invoice сатри ҳаволали
 бўлса - ўша сотув ҳаракатининг бирлик таннархи; ҳаволасиз -
 жорий сиёсат таннархи. FIFO'да қайтим ЯНГИ қатлам бўлиб киради.
- Чиқим (VendorCredit): жорий сиёсат таннархида (adjustment нақши);
 ҳужжат net'и билан фарқ shrinkage счётига. Салбий қолдиқ
 ҳимоялари (BR-INV) одатдагидек ишлайди.
- Ҳамма ҳаракат (item, warehouse) кесимида - омбор сатрда танланади.

## Валидация ва инвариантлар (BR-RET - аввал каталогга)

- BR-RET-001: камида битта сатр; сумма/миқдор мусбат.
- BR-RET-002: inventory сатрида омбор шарт.
- BR-RET-003: application суммаси кредитнинг очиқ қолдиғидан ва
 ҳужжатнинг очиқ balance'идан ошмайди.
- BR-RET-004: application фақат бир хил валютадаги ҳужжатга
 (QBO услуби; кросс-валюта кредит қўллаш - 2-босқич).
- BR-RET-005: application фақат ўша контактнинг ҳужжатига.
- BR-RET-006: асл ҳужжат ҳаволаси танланса қайтариш миқдори асл
 сатр миқдоридан ошмайди (қисман қайтариш мумкин).
- BR-RET-007: қўлланган кредит reverse қилинмайди - аввал
 application'лар бекор қилинади (unapply).
- BR-RET-008: қайтариш ҳужжати (CM/VC/RR учаласига битта код)
 валютаси контакт валютасига мос бўлиши шарт (Contact.currency,
 null = home; QBO қатъий, Arbitr-087) - server валютани контактдан
 ЎЗИ олади.
- ҚҚС: ставка snapshot асл ҳужжатдагидек (ҳаволали prefill'да асл
 ставка олинади - орада ставка ўзгарган бўлса ҳам тўғри қайтим).

## Тестлар (мажбурий рўйхат)

1. CreditMemo post: Dr даромад+ҚҚС / Cr AR, debit==credit;
 inventory сатрда StockMovement IN + Dr INVENTORY / Cr COGS.
2. Ҳаволали қайтим - асл сотув таннархида; ҳаволасиз - жорий AVCO.
3. Apply: invoice balance камаяди, кредит қолдиғи камаяди; BR-RET-003
 ошиқча суммада; кросс-валютада BR-RET-004.
4. Apply FX: кредит курси ≠ invoice курси - фарқ JE
 (CREDIT_APPLICATION), нол фарқда JE йўқ.
5. RefundReceipt: Cr банк, AR тегилмайди.
6. VendorCredit: Dr AP / Cr INVENTORY (сиёсат таннархи) + фарқ
 shrinkage'га; хизмат сатри Cr EXPENSE; input ҚҚС Cr.
7. BR-RET-007: қўлланган кредит reverse рад; unapply'дан кейин ўтади.
8. Reverse: тўлиқ сторно + StockMovement қайтими.
9. ScreenSmokeTest: учала рўйхат/форма/кўриш.

## Экранлар (JTE routes)

- `/credit-memos`, `/vendor-credits`, `/refund-receipts` - рўйхат +
 янги (FULL транзакция формаси, invoice/bill форма қолипи: сатрлар,
 жонли жами/ҚҚС, UoM, курс prefill data-autofill нақши) + кўриш
 (reverse шу ерда).
- CreditMemo/VendorCredit кўришида **«Қўллаш» бўлими**: контактнинг
 очиқ invoice/bill'лари рўйхати + сумма киритиш + unapply.
- Invoice/Bill кўришида қўлланган кредитлар кўринади (balance
 изоҳида).
- «+ Янги» менюга учта банд (калитлар аввал grep); сайдбар: Сотув
 гуруҳига CreditMemo/RefundReceipt, Харид гуруҳига VendorCredit.
- Invoice/Bill кўришидан «Қайтариш яратиш» тугмаси (ҳаволали
 prefill) - QBO оқими.

## 2-босқич (ҳозир ЭМАС)

- Кросс-валюта application; credit'ни бошқа контактга ўтказиш;
 қайтариш сабаби каталоги; печат формалари.
