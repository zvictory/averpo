package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.JournalEntry;

/**
 * Проводка сторно қилинди - PostingService {@code reverse}
 * муваффақиятли якунида эълон қилинади. Сторно ҳам янги POSTED entry,
 * лекин алоҳида ҳодиса тури: аудит экранида «пост» ва «сторно» аниқ
 * фарқлансин (docs/modules/audit-log.md).
 *
 * <p>Event ledger ичида туради (қоида №6 - JournalEntryPostedEvent
 * изоҳига қаранг).
 *
 * @param reversal янги сторно entry'си
 * @param original сторно қилинган асл entry
 *
 * @author Zafar
 */
public record JournalEntryReversedEvent(JournalEntry reversal, JournalEntry original) {
}
