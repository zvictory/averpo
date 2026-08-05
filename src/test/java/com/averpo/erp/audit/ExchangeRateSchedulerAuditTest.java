package com.averpo.erp.audit;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.ExchangeRateImportedEvent;
import com.averpo.erp.shared.service.ExchangeRateScheduler;
import com.averpo.erp.shared.service.ExchangeRateService;
import com.averpo.erp.shared.service.ExchangeRateService.ImportResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DEC-164/168: ЦБ авто-курс импорти /audit-log'га ёзилиши - муваффақият
 * ҲАМ, хато ҲАМ, ва санагич ҳалоллиги («ўзгарди» фақат курс ўзгарса).
 * Scheduler {@link ExchangeRateService}'ни сохта билан юргизади (тармоқ/
 * DB'сиз): success йўли {@link ImportResult} қайтаради, хато йўли throw
 * қилади.
 *
 * <p>Класс АТАЙЛАБ {@code @Transactional} эмас: тингловчи REQUIRES_NEW
 * билан ёзади (аудит импорт транзакциясидан мустақил), шунинг учун ёзув
 * real commit бўлади - тест framework rollback'и уни тозаламайди.
 * {@code @AfterEach} фақат шу турдаги ёзувларни ўчиради (умумий тест
 * базасини ифлосламаслик учун; repository.delete тест-only, prod
 * append-only контракти ўзгармайди - service'да ўчириш йўли йўқ).
 */
@SpringBootTest
@ActiveProfiles("test")
class ExchangeRateSchedulerAuditTest {

    @Autowired ExchangeRateScheduler scheduler;
    @Autowired AuditEventRepository auditRepository;

    /** Импорт мантиғи сохта - фақат scheduler'нинг event publish йўлини синаймиз. */
    @MockitoBean ExchangeRateService exchangeRateService;

    @AfterEach
    void tearDown() {
        // REQUIRES_NEW listener ёзувни real commit қилади - фақат шу турни тозалаймиз
        auditRepository.deleteAll(imported());
    }

    /** Шу турдаги ёзувлар - кичик тўпламда findAll кифоя (AuditLogTest нақши). */
    private List<AuditEvent> imported() {
        return auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.EXCHANGE_RATE_IMPORTED)
                .toList();
    }

    /** Муваффақиятли импорт (курс ўзгарган): details «текширилди/ўзгарди», actor «Тизим». */
    @Test
    void successfulImport_writesAuditWithCountsAndSystemActor() {
        given(exchangeRateService.importFromCbu(any())).willReturn(new ImportResult(5, 3, 2));

        scheduler.importDaily();

        List<AuditEvent> events = imported();
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getUsername()).isEqualTo(AuditLogService.SYSTEM_ACTOR);
        assertThat(event.getDetails()).isEqualTo("5 валюта текширилди, 3 ўзгарди, 2 ўтказилди");
    }

    /**
     * DEC-168: курс ўзгармаган импорт (дам олиш) - «янгиланди» ЭМАС,
     * «курслар ўзгармади» деб ҳалол ёзилади (фойдаланувчи адашмасин).
     */
    @Test
    void unchangedImport_writesRatesUnchangedDetails() {
        given(exchangeRateService.importFromCbu(any())).willReturn(new ImportResult(5, 0, 0));

        scheduler.importDaily();

        List<AuditEvent> events = imported();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getDetails())
                .isEqualTo("5 валюта текширилди, курслар ўзгармади, 0 ўтказилди");
    }

    /**
     * Хато импорт: importFromCbu ЎЗ транзакциясини rollback қилса ҳам (throw),
     * аудит ЯНГИ мустақил транзакцияда сақланади - scheduler catch иловани
     * йиқитмайди, details'да хато сабаби.
     */
    @Test
    void failedImport_writesAuditInSeparateTransaction_withReason() {
        given(exchangeRateService.importFromCbu(any()))
                .willThrow(new BusinessRuleException(BusinessRule.BR_FX_003,
                        "Home валюта (EUR) ЦБ рўйхатида йўқ"));

        assertThatCode(() -> scheduler.importDaily()).doesNotThrowAnyException();

        List<AuditEvent> events = imported();
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getUsername()).isEqualTo(AuditLogService.SYSTEM_ACTOR);
        assertThat(event.getDetails())
                .startsWith("амалга ошмади:")
                .contains("ЦБ рўйхатида йўқ");
    }

    /**
     * DEC-168 (Асрорхўжа-017) B шохи: success publish try/catch'дан
     * ТАШҚАРИДА - аудит commit хатоси муваффақ импортни FAILURE қилмайди.
     * Local mock publisher: success event publish'да throw қилдирилади;
     * importFromCbu аллақачон қайтган (импорт T1'да commit) - хато
     * кўтарилади, лекин FAILURE аудити ЁЗИЛМАЙДИ (catch фақат импорт
     * хатосини ушларди, аудит хатосини эмас).
     */
    @Test
    void successPublishThrows_importDailyPropagates_noFailureAudit() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ExchangeRateScheduler isolated = new ExchangeRateScheduler(exchangeRateService, publisher);
        given(exchangeRateService.importFromCbu(any())).willReturn(new ImportResult(5, 3, 2));
        RuntimeException auditFail = new RuntimeException("аудит commit хатоси (pool тугади)");
        doThrow(auditFail).when(publisher).publishEvent(any(ExchangeRateImportedEvent.class));

        // Аудит хатоси кўтарилади (ютилмайди) - муваффақ импорт FAILURE'га айланмайди
        assertThatThrownBy(isolated::importDaily).isSameAs(auditFail);

        // Фақат БИТТА publish (success) юборилди; FAILURE publish ЙЎҚ - success
        // try'дан ташқарида бўлгани учун catch унга уланмайди
        ArgumentCaptor<ExchangeRateImportedEvent> captor =
                ArgumentCaptor.forClass(ExchangeRateImportedEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().isFailure()).isFalse();
    }
}
