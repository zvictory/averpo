package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.JournalEntry;

/**
 * GL'га проводка ёзилди - PostingService {@code post} муваффақиятли
 * якунида эълон қилинади (createAndPost ҳам, draft'ни алоҳида post
 * қилиш ҳам шу нуқтадан ўтади - қамров тўлиқ).
 *
 * <p>Event ledger ичида туради, тингловчи audit модулида: қоида №6 -
 * ledger ҳеч кимга боғлиқ эмас, audit'ни import қилмайди
 * (docs/modules/audit-log.md). Синхрон тингланади - чақирувчи
 * транзакцияси rollback бўлса аудит ёзуви ҳам йўқолади.
 */
public record JournalEntryPostedEvent(JournalEntry entry) {
}
