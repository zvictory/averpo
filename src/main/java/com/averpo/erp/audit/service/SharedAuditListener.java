package com.averpo.erp.audit.service;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.plugins.core.service.PluginToggledEvent;
import com.averpo.erp.shared.service.CompanySettingsChangedEvent;
import com.averpo.erp.shared.service.ExcelImportService.ImportResult;
import com.averpo.erp.shared.service.ExcelImportedEvent;
import com.averpo.erp.shared.service.ExchangeRateImportedEvent;
import com.averpo.erp.shared.service.FactoryResetEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared ва plugins модуллари ҳодисаларини (созламалар, заводга қайтариш,
 * Excel import, плагин toggle) аудитга ёзади (DEC-062). Боғлиқлик
 * йўналиши {@link LedgerAuditListener} нақши: audit → манба модул (event
 * record'ларини import қилади), манба эса audit'ни билмайди - тескариси
 * цикл бўларди (audit BaseEntity орқали shared'га боғлиқ).
 *
 * <p>АТАЙЛАБ синхрон {@code @EventListener}: ёзув чақирувчи транзакциясининг
 * ўзида боради - rollback бўлса аудит ёзуви ҳам йўқолади. FACTORY_RESET
 * учун бу тартиб ҳам муҳим: reset TRUNCATE'дан кейин publish қилади,
 * шунда ёзув тоза журналнинг биринчи қатори бўлиб қолади.
 */
@Component
@RequiredArgsConstructor
public class SharedAuditListener {

    /** Ёзишнинг ягона йўли. */
    private final AuditLogService auditLogService;

    /** Созламалар ўзгарди: details event'дан тайёр келади (фақат ўзгарган майдонлар). */
    @EventListener
    public void onSettingsChanged(CompanySettingsChangedEvent event) {
        auditLogService.record(AuditEventType.SETTINGS_CHANGED,
                AuditLogService.currentUsername(), null, null, event.details(), null);
    }

    /** Заводга қайтариш - тоза журналнинг биринчи ёзуви (reset изоҳига қаранг). */
    @EventListener
    public void onFactoryReset(FactoryResetEvent event) {
        auditLogService.record(AuditEventType.FACTORY_RESET,
                AuditLogService.currentUsername(), null, null,
                "Барча иш маълумоти тозаланди, seed каталоглар қайта ўрнатилди",
                null);
    }

    /**
     * Плагин ёқилди/ўчирилди (DEC-113): details'да калит + янги ҳолат -
     * ким/қачон AuditLogService'нинг ўзидан (username/IP/UA/вақт).
     */
    @EventListener
    public void onPluginToggled(PluginToggledEvent event) {
        auditLogService.record(AuditEventType.PLUGIN_TOGGLED,
                AuditLogService.currentUsername(), null, null,
                event.key().name() + ": " + (event.enabled() ? "ёқилди" : "ўчирилди"),
                null);
    }

    /** Excel import қўлланди: details'да туркумлаб сонлар. */
    @EventListener
    public void onExcelImported(ExcelImportedEvent event) {
        auditLogService.record(AuditEventType.IMPORT_EXCEL,
                AuditLogService.currentUsername(), null, null,
                importDetails(event.result()), null);
    }

    /**
     * ЦБ авто-курс импорти якунланди (DEC-164, санагич DEC-168):
     * муваффақият «N текширилди, M ўзгарди, K ўтказилди» ёки хато «амалга
     * ошмади: сабаб». Actor доим {@link AuditLogService#SYSTEM_ACTOR}
     * («Тизим») - фон scheduler'да фойдаланувчи ЙЎҚ, currentUsername()
     * ЭМАС (у pool thread'да эски контекстдан адашиши мумкин).
     *
     * <p>@Transactional REQUIRES_NEW АТАЙЛАБ: хато йўлида importFromCbu ўз
     * транзакциясини rollback қилган - аудит ЯНГИ мустақил транзакцияда
     * сақланмаса «хатони ҳам ёз» талаби ишламасди. Бошқа listener'лар
     * (settings/plugin/excel) чақирувчи транзакциясида қолади (rollback
     * билан бирга йўқолиши КЕРАК); бу ёзув эса импорт натижасидан
     * мустақил - фон жараён ҳақиқатан юрди, изи қолиши шарт.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExchangeRateImported(ExchangeRateImportedEvent event) {
        auditLogService.record(AuditEventType.EXCHANGE_RATE_IMPORTED,
                AuditLogService.SYSTEM_ACTOR, null, null, importedDetails(event), null);
    }

    /**
     * ЦБ импорти details матни (DEC-168 - ҳалол санагич): муваффақиятда
     * «N валюта текширилди, {M ўзгарди|курслар ўзгармади}, K ўтказилди»,
     * хатода «амалга ошмади: сабаб». «Янгиланди» сўзи АТАЙЛАБ ЙЎҚ - дам
     * олишда ЦБ курсни ўзгартирмайди (жума курсини қайтаради), фойдаланувчи
     * «ўзгарди»ни кўриб тизимдан бекорга ўзгариш қидирмасин.
     */
    private static String importedDetails(ExchangeRateImportedEvent event) {
        if (event.isFailure()) {
            return "амалга ошмади: " + event.errorMessage();
        }
        String changePart = event.changed() > 0
                ? event.changed() + " ўзгарди"
                : "курслар ўзгармади";
        return event.checked() + " валюта текширилди, " + changePart
                + ", " + event.skipped() + " ўтказилди";
    }

    /**
     * Import details матни (spec намунаси: «яратилди: 12 контакт, 34 товар;
     * ўтказилди: 3») - фақат ноль бўлмаган туркумлар саналади.
     */
    private static String importDetails(ImportResult result) {
        List<String> created = new ArrayList<>();
        appendCount(created, result.contactsCreated(), "контакт");
        appendCount(created, result.employeesCreated(), "ходим");
        appendCount(created, result.itemsCreated(), "товар");
        appendCount(created, result.warehousesCreated(), "омбор");
        appendCount(created, result.accountsCreated(), "счёт");
        return "яратилди: " + (created.isEmpty() ? "0" : String.join(", ", created))
                + "; ўтказилди: " + result.totalSkipped();
    }

    /** Ноль бўлмаган туркум сонини «N label» кўринишида қўшади. */
    private static void appendCount(List<String> out, int count, String label) {
        if (count > 0) {
            out.add(count + " " + label);
        }
    }
}
