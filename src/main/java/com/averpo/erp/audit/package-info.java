/**
 * АУДИТ ЖУРНАЛИ. Append-only ҳодисалар журнали - update/delete API
 * умуман йўқ. Тўлиқ spec: docs/modules/audit-log.md.
 *
 * <p>Боғлиқлик шартномаси: audit → ledger ФАҚАТ event record'лари
 * (JournalEntryPostedEvent/JournalEntryReversedEvent - ledger ҳеч
 * кимга боғлиқ эмаслиги бузилмайди); audit → security ТАҚИҚ (цикл
 * чиқади) - auth ҳодисалари фақат Spring Security framework
 * event'ларидан тингланади. Ёзишнинг ягона йўли -
 * AuditLogService.record.
 */
package com.averpo.erp.audit;
