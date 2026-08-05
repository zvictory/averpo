# DocumentSequence - SPEC

## Мақсад
Барча ҳужжат турлари учун умумий, тур бўйича созланадиган рақамлаш:
INV-2026-00001, BILL-2026-00001, PAY-2026-00001, JE-2026-000001.
Эски лойиҳа ғояси (docs/old-erp-ideas.md §1) - жадвал асосидаги
sequence, DB sequence объекти эмас. JE'нинг мавжуд рақамлаши шу
service'га кўчади.

## Entity'лар

`shared.domain.DocumentSequence` (жадвал: `document_sequence`):

| Майдон | Тип | Изоҳ |
|---|---|---|
| document_type | varchar(30), unique | `DocumentType` enum: JOURNAL_ENTRY, INVOICE, BILL, PAYMENT ва бошқа ҳужжат турлари - ҳар модул ўз турини қўшади (тўлиқ рўйхат: DocumentType.java) |
| prefix | varchar(10) | Рақам олдидаги белги: JE, INV, BILL, PAY |
| include_year | boolean | Рақамда йил кўрсатиладими (JE-2026-...) |
| padding | int, CHECK 1..12 | Рақам узунлиги: 5 → 00001; JE'да 6 (мавжуд форматга мос) |
| next_number | bigint, CHECK > 0 | Кейинги бериладиган рақам |

Эски лойиҳадаги `format_pattern` устуни ОЛИНМАДИ - include_year +
padding етарли, формат доим `{prefix}-{йил?}-{рақам}`. Керак бўлса
кейин қўшилади.

## Service API

`shared.service.DocumentSequenceService`:

- `String next(DocumentType type, LocalDate documentDate)` - навбатдаги
  рақамни ажратиб, форматлаб қайтаради. `documentDate` фақат йил
  кўрсатиш учун; рақам йилга боғлиқ ЭМАС - йил алмашганда узилмай
  давом этади (JE-2026-000123 дан кейин JE-2027-000124).

Транзакция: `Propagation.MANDATORY` - рақам ҳужжат яратилаётган
транзакция ичида олинади, ҳужжат сақланмай қолса рақам ҳам куймайди
(gap олдини олади). Race'га қарши: sequence қатори
`PESSIMISTIC_WRITE` (SELECT ... FOR UPDATE) билан қулфланади -
parallel иккита ҳужжат бир рақам ололмайди.

## Posting
GL'га тегмайди - PostingService энди JE рақамини шу service'дан олади
(эски `journal_entry_number_seq` DB sequence ўчирилди, рақам
`document_sequence.next_number`'дан давом этади).

## Валидация ва инвариантлар
- BR-SEQ-001: ҳужжат тури учун sequence қатори топилиши шарт
  (ҳар тур ўз модули changeset'ида seed қилинади; йўқолиши deploy
  хатоси).
- next_number фақат олдинга юради; DB CHECK: next_number > 0,
  padding 1..12.
- Рақам ҳужжат яратиш транзакцияси ичида олинади (MANDATORY) -
  ташқарида чақириш хато.

## Тестлар (мажбурий рўйхат)
- Формат: prefix + йил + padding тўғри (INV-2026-00001).
- Кетма-кетлик: иккита чақириқ кетма-кет рақам беради.
- Йил алмашганда рақам давом этади (reset йўқ).
- Sequence қатори йўқ тур учун BR-SEQ-001.
- Parallel: бир нечта транзакция бир вақтда next чақирса рақамлар
  такрорланмайди (row lock тести).
- PostingService орқали JE рақами эски форматда давом этади
  (мавжуд PostingServiceTest'лар қоплайди).

## Экранлар (JTE routes)
Ҳозирча йўқ - созлаш экрани керак бўлганда /settings остига қўшилади.
