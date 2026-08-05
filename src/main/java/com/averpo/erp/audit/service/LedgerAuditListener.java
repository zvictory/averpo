package com.averpo.erp.audit.service;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.ledger.service.AccountChangedEvent;
import com.averpo.erp.ledger.service.ChartImportedEvent;
import com.averpo.erp.ledger.service.JournalEntryPostedEvent;
import com.averpo.erp.ledger.service.JournalEntryReversedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ledger post/reverse ҳодисаларини аудитга ёзади
 * (docs/modules/audit-log.md). Боғлиқлик йўналиши қоида №6 га мос:
 * audit → ledger (event record'ларини import қилади), ledger эса
 * audit'ни билмайди.
 *
 * <p>АТАЙЛАБ синхрон {@code @EventListener}
 * (@TransactionalEventListener ЭМАС): ёзув чақирувчи (PostingService)
 * транзакциясининг ўзида боради - кейинроқ rollback бўлса аудит ёзуви
 * ҳам йўқолади. Журнал фақат ҳақиқатан содир бўлган ишни акс
 * эттиради (spec, rollback исботи тести).
 */
@Component
@RequiredArgsConstructor
public class LedgerAuditListener {

    /** Ёзишнинг ягона йўли. */
    private final AuditLogService auditLogService;

    /** Проводка ёзилди: entry id + рақам снапшоти + description. */
    @EventListener
    public void onPosted(JournalEntryPostedEvent event) {
        auditLogService.record(AuditEventType.JE_POSTED,
                AuditLogService.currentUsername(),
                event.entry().getId(), event.entry().getEntryNumber(),
                event.entry().getDescription(), null);
    }

    /** Сторно: ёзув сторно entry'сига, details'да асл entry рақами. */
    @EventListener
    public void onReversed(JournalEntryReversedEvent event) {
        auditLogService.record(AuditEventType.JE_REVERSED,
                AuditLogService.currentUsername(),
                event.reversal().getId(), event.reversal().getEntryNumber(),
                "Сторно: " + event.original().getEntryNumber(), null);
    }

    /**
     * Default chart импорти (DEC-062): details «яратилди N, ўтказилди M»
     * - қўлда тугма, авто-init ва factory reset учаласи шу ҳодисага киради.
     */
    @EventListener
    public void onChartImported(ChartImportedEvent event) {
        auditLogService.record(AuditEventType.CHART_IMPORTED,
                AuditLogService.currentUsername(), null, null,
                "яратилди " + event.created() + ", ўтказилди " + event.skipped(),
                null);
    }

    /**
     * Счёт create/update/deactivate (DEC-062): details = «ном»
     * (detail type), таҳрирда ундан кейин ўзгарган майдонлар диффи.
     */
    @EventListener
    public void onAccountChanged(AccountChangedEvent event) {
        AuditEventType type = switch (event.action()) {
            case CREATED -> AuditEventType.ACCOUNT_CREATED;
            case UPDATED -> AuditEventType.ACCOUNT_UPDATED;
            case DEACTIVATED -> AuditEventType.ACCOUNT_DEACTIVATED;
        };
        auditLogService.record(type, AuditLogService.currentUsername(), null, null,
                "«" + event.account().getName() + "» (" + event.account().getDetailType() + ")"
                        + (event.changes() == null ? "" : "; " + event.changes()),
                null);
    }
}
