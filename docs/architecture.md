# Архитектура қарорлари

Боғлиқ ҳужжатлар: темир қоидалар - engineering-rules.md; қуриш кетма-кетлиги -
docs/roadmap.md; проводка эталони - docs/posting-rules.md; бизнес
қоидалар каталоги - docs/business-rules.md; модул спецлари -
docs/modules/. (Янгиланди, Ulugbek-022/023.)

## 1. Modular Monolith
Микросервис эмас. Package-by-module: com.averpo.erp.<module>
(21б тўлиқ rebrand'да пакет com.averpo га кўчади - roadmap 5-банд).
Модуллар фақат service интерфейс орқали гаплашади.
Боғлиқлик йўналиши: барча модуллар → ledger (тескариси тақиқ).

Модуллар (ҳолати): shared, ledger, contact, item,
inventory, sales, purchase, bank (спецда banking.md), tax, payroll,
pricing, attachment, audit, search, security, dashboard, report,
i18n, config, web.

## 2. Ledger - ягона ҳақиқат манбаи
- Ҳеч бир модул balance'ни ўзи сақламайди. Balance = ledger'дан ҳисоб.
- Ҳужжат (Invoice, Bill, Payment...) post қилинганда PostingService
 орқали JournalEntry яратилади. sourceModule + sourceDocumentId
 билан боғланади.
- POSTED entry immutable. Хато - reverse (сторно) билан тузатилади.

## 3. Multi-currency
- Home currency CompanySettings жадвалида (default UZS). Ledger home
 валютада балансланади. Биринчи POSTED entry'дан кейин home currency
 ўзгартирилмайди (QuickBooks услуби).
- Money embeddable: amount + currency + baseAmount + exchangeRate.
 baseAmount доим home валютада.
- ExchangeRate жадвали APPEND-ONLY (033-changeset): бир
 (валюта, сана)га кўп ёзув мумкин, амалдаги курс = энг охирги ёзув
 (сана, кейин UUIDv7 id) - тарих ўчмайди. Тафсилот:
 docs/modules/multi-currency.md.
- Курс фарқи: тўлов курси ≠ ҳужжат курси бўлса, фарқ автоматик
 EXCHANGE_GAIN_OR_LOSS тизим счётига (битта счёт, QuickBooks услуби)
 проводка қилинади: фойда - кредит, зарар - дебет.
- Realized фарқ - тўлов пайтида. Unrealized (қайта баҳолаш) - кейинги босқич.

## 4. Inventory - AVCO ёки FIFO, (item, warehouse) даражасида
- Метод компания даражасида: CompanySettings.inventoryValuation
 (default AVCO). Биринчи StockMovement'дан кейин қулфланади
 (InventoryValuationLock порти - home currency паттерни).
- **AVCO**: StockBalance PK (item_id, warehouse_id): qty + avgCost.
 Кирим: newAvg = (qty*avg + inQty*inCost) / (qty+inQty).
 Чиқим: COGS = outQty * avg.
- **FIFO**: киримлар inventory_cost_layer жадвалида сақланади
 (item_id, warehouse_id, received_date, qty_remaining, unit_cost).
 Чиқим энг эски layer'лардан кетма-кет ейилади; COGS = ейилган
 layer'лар суммаси. QBO Advanced ҳам FIFO ишлатади.
- Иккала методда ҳам qty манфий бўлиши тақиқ (валидация).
- Transfer: икки movement (OUT + IN), GL проводкасиз (FIFO'да layer
 кўчирилади, қиймат ўзгармайди).
- Landed cost (6-босқич): receipt'га retroactive қўшилади,
 INVENTORY_CLEARING счёти орқали; сотилган қисм фарқи COGS'га
 (FIFO'да тегишли layer'лар қиймати ошади).

## 5. Ҳужжат ҳаёт цикли
DRAFT → POSTED → (REVERSED)
- DRAFT: эркин таҳрир.
- POSTED: read-only, GL'да проводка бор.
- REVERSED: сторно entry яратилган.

## 6. Персистенция
- UUIDv7 primary key (shared.Uuid7, вақт бўйича тартибли - index'га
 қулай), BaseEntity: id, version, createdAt, updatedAt, createdBy
 (аудит - ким яратгани, user-management.md).
 Id қўлда тайинланади, BaseEntity Persistable орқали isNew'ни бошқаради.
- Пул: NUMERIC(19,4), курс: NUMERIC(24,12) - тескари йўналишдаги
 курслар (1 UZS = 0.000081967213 USD) ҳам 8 маъноли рақам билан
 сақланиши учун. Java: BigDecimal.
- Liquibase - ягона schema манбаи. hibernate ddl-auto=validate.
- journal_entry_line - энг катта жадвал: (account_id, entry_date) index,
 кейинчалик ой бўйича partitioning.

## 7. UI
- JTE + HTMX + Alpine.js. Server-side render, partial update HTMX билан.
- Layout: src/main/jte/layout/main.jte. Модул саҳифалари ўз папкасида.
- Форма → POST → redirect ёки HTMX partial. JSON API фақат зарур жойда.
