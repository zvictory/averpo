package com.averpo.erp.audit;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Аудит қамрови кенгайиши тестлари (DEC-062, spec «Кенгайиш»
 * бўлими): SETTINGS_CHANGED фақат ўзгарган майдонлар билан,
 * ACCOUNT_CREATED/UPDATED/DEACTIVATED дифф билан, CHART_IMPORTED сонлар
 * билан. FACTORY_RESET - FactoryResetServiceTest'да (TRUNCATE у ерда),
 * IMPORT_EXCEL - ExcelImportServiceTest'да, LOGOUT/user_agent -
 * LogoutAuditTest'да (web оқим).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "kengaytiruvchi")
class AuditExpansionTest {

    @Autowired AccountService accountService;
    @Autowired CompanySettingsService settingsService;
    @Autowired AuditEventRepository auditRepository;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
    }

    /** Тур бўйича ёзувлар - тест кичик тўпламда findAll кифоя (AuditLogTest нақши). */
    private List<AuditEvent> eventsOfType(AuditEventType type) {
        return auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == type).toList();
    }

    /**
     * Settings update: details'да ФАҚАТ ўзгарган майдонлар «эски → янги»;
     * айнан бир хил қайта сақлаш янги ёзув бермайди (шовқин йўқ).
     */
    @Test
    void settingsUpdate_writesChangedFieldsOnly_noEventWhenUnchanged() {
        var settings = settingsService.get();
        String timezone = settings.getTimezone();
        settingsService.update("Аудит компанияси", settings.homeCurrencyCode(),
                timezone, null, LocalDate.of(2026, 6, 30));

        List<AuditEvent> changed = eventsOfType(AuditEventType.SETTINGS_CHANGED);
        assertThat(changed).hasSize(1);
        AuditEvent event = changed.get(0);
        assertThat(event.getUsername()).isEqualTo("kengaytiruvchi");
        assertThat(event.getDetails())
                .contains("name: Компания → Аудит компанияси")
                .contains("closingDate: - → 2026-06-30")
                // Ўзгармаган майдонлар диффга кирмайди
                .doesNotContain("timezone").doesNotContain("homeCurrency");

        // Айнан бир хил қиймат билан қайта сақлаш - янги ёзув ЙЎҚ
        settingsService.update("Аудит компанияси", settings.homeCurrencyCode(),
                timezone, null, LocalDate.of(2026, 6, 30));
        assertThat(eventsOfType(AuditEventType.SETTINGS_CHANGED)).hasSize(1);
    }

    /** Счёт lifecycle: create → CREATED, таҳрир → UPDATED (дифф), нофаол → DEACTIVATED. */
    @Test
    void accountLifecycle_writesCreatedUpdatedDeactivated() {
        Account account = accountService.create("Аудит синов счёти",
                AccountDetailType.CHECKING, null, null, null, true, null);

        List<AuditEvent> created = eventsOfType(AuditEventType.ACCOUNT_CREATED);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getDetails())
                .contains("Аудит синов счёти").contains("CHECKING");
        assertThat(created.get(0).getUsername()).isEqualTo("kengaytiruvchi");

        // Таҳрир: фақат ном ўзгаради - диффда айнан шу кўринади
        accountService.update(account.getId(), "Аудит синов счёти 2",
                AccountDetailType.CHECKING, null, null, null, true, null, true);
        List<AuditEvent> updated = eventsOfType(AuditEventType.ACCOUNT_UPDATED);
        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).getDetails())
                .contains("name: Аудит синов счёти → Аудит синов счёти 2")
                .doesNotContain("postable");

        // Ўзгаришсиз қайта сақлаш - янги ёзув ЙЎҚ
        accountService.update(account.getId(), "Аудит синов счёти 2",
                AccountDetailType.CHECKING, null, null, null, true, null, true);
        assertThat(eventsOfType(AuditEventType.ACCOUNT_UPDATED)).hasSize(1);

        // Нофаол қилиш - алоҳида тур, диффда active кўринади
        accountService.update(account.getId(), "Аудит синов счёти 2",
                AccountDetailType.CHECKING, null, null, null, true, null, false);
        List<AuditEvent> deactivated = eventsOfType(AuditEventType.ACCOUNT_DEACTIVATED);
        assertThat(deactivated).hasSize(1);
        assertThat(deactivated.get(0).getDetails())
                .contains("Аудит синов счёти 2")
                .contains("active: true → false");
    }

    /**
     * Chart импорти: биринчи юклаш «яратилди N» из қолдиради (setUp'даги
     * import), идемпотент қайта чақириқ «яратилди 0» билан алоҳида ёзув -
     * қўлда тугма ва авто-init иккиси шу нуқтадан ўтади (spec).
     */
    @Test
    void chartImport_writesChartImported_alsoOnIdempotentRerun() {
        List<AuditEvent> first = eventsOfType(AuditEventType.CHART_IMPORTED);
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getDetails()).startsWith("яратилди ")
                .doesNotStartWith("яратилди 0");

        accountService.importDefaultChart();
        List<AuditEvent> after = eventsOfType(AuditEventType.CHART_IMPORTED);
        assertThat(after).hasSize(2);
        // Иккинчиси идемпотент: ҳаммаси ўтказилди
        assertThat(after.stream()
                .filter(e -> e.getDetails().startsWith("яратилди 0, ўтказилди "))
                .count()).isEqualTo(1);
    }
}
