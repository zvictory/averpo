package com.averpo.erp.shared;

import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.domain.ClassTrackingMode;
import com.averpo.erp.shared.domain.TxnClass;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.TxnClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Class tracking форма хулқи (class-tracking.md «Тестлар» 6-7):
 * PER_TXN - сарлавҳа танлови ҳамма сатрга тарқалади (controller),
 * OFF - формада class майдони умуман render бўлмайди. Режим
 * CompanySettings'да - тест ичида алмаштирилади (қулф йўқ).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class ClassTrackingWebTest {

    @Autowired WebApplicationContext context;
    @Autowired CompanySettingsService settingsService;
    @Autowired TxnClassService txnClassService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    private MockMvc mockMvc;
    private TxnClass filial;
    private UUID bank;
    private UUID cash;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        accountService.importDefaultChart();
        filial = txnClassService.create("Филиал Т", null);
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        cash = accountRepository.findByName("Касса").orElseThrow().getId();
    }

    /** Spec 6-банд: PER_TXN - сарлавҳа танлови ҳамма сатрга тарқалади. */
    @Test
    void perTxnMode_headerClassSpreadsToAllLines() throws Exception {
        settingsService.changeTrackClasses(ClassTrackingMode.PER_TXN);

        mockMvc.perform(post("/journal-entries").with(csrf())
                        .param("action", "post")
                        .param("entryDate", "2026-07-08")
                        .param("description", "PER_TXN тарқатиш тести")
                        .param("classId", filial.getId().toString())
                        .param("lines[0].accountId", cash.toString())
                        .param("lines[0].debitAmount", "5000")
                        .param("lines[1].accountId", bank.toString())
                        .param("lines[1].creditAmount", "5000"))
                .andExpect(status().is3xxRedirection());

        JournalEntry entry = entryRepository.findAll().stream()
                .filter(e -> "PER_TXN тарқатиш тести".equals(e.getDescription()))
                .findFirst().orElseThrow();
        assertThat(entry.getLines()).hasSize(2);
        for (JournalEntryLine line : entry.getLines()) {
            // Иккала сатр ҳам сарлавҳадаги битта class'ни олди
            assertThat(line.getClassId()).isEqualTo(filial.getId());
        }
    }

    /** Spec 7-банд: OFF - class майдонлари умуман чиқмайди; режимлар фарқи. */
    @Test
    void offMode_formHasNoClassFields() throws Exception {
        // Default OFF: на сарлавҳа, на сатр class input'и
        mockMvc.perform(get("/journal-entries/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("classId"))));

        // PER_LINE: сатрда select бор, сарлавҳада йўқ
        settingsService.changeTrackClasses(ClassTrackingMode.PER_LINE);
        mockMvc.perform(get("/journal-entries/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lines[0].classId")))
                .andExpect(content().string(not(containsString("name=\"classId\""))));

        // PER_TXN: сарлавҳада битта select, сатрларда йўқ
        settingsService.changeTrackClasses(ClassTrackingMode.PER_TXN);
        mockMvc.perform(get("/journal-entries/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"classId\"")))
                .andExpect(content().string(not(containsString("lines[0].classId"))));

        // Invoice формасида ҳам OFF - class йўқ (қамров: иккинчи оқим)
        settingsService.changeTrackClasses(ClassTrackingMode.OFF);
        mockMvc.perform(get("/invoices/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("classId"))));
    }
}
