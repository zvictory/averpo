package com.averpo.erp.item;

import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.TestRoles;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitr-147б: /settings/units саҳифасидаги «Стандарт гуруҳларни юклаш»
 * тугмаси (POST /settings/units/import-default) - chart importDefault
 * кўзгуси. 147 installer'ни мавжуд базада ҳам ишга солади.
 *
 * <p>Гейт: /settings/units/** INVENTORY соҳасида - POST editAuthority
 * талаб қилади (WAREHOUSE_MANAGER киради, VIEWER йўқ). CSRF мажбурий:
 * токенсиз POST {@code CsrfException} билан {@code /login?expired} га
 * (Arbitr-096 семантикаси, ProfileWebTest каби). Роль: WAREHOUSE_MANAGER
 * (INVENTORY EDIT - combobox тестидаги /warehouses/quick билан бир хил).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(value = UserRole.WAREHOUSE_MANAGER, username = "omborchi")
class UnitImportDefaultWebTest {

    /** Кутилган стандарт гуруҳлар (Arbitr-147 тўплами). */
    private static final List<String> STANDARD_GROUPS =
            List.of("Дона", "Оғирлик", "Узунлик", "Ҳажм", "Юза", "Вақт");

    @Autowired WebApplicationContext context;
    @Autowired UnitService unitService;

    /** Security filter chain уланган MockMvc (VIEWER override учун ҳам). */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Жорий гуруҳ номлари. */
    private List<String> groupNames() {
        return unitService.groups().stream().map(UnitGroup::getName).toList();
    }

    @Test
    void importDefault_createsStandardGroups_redirectsToGroups() throws Exception {
        mockMvc.perform(post("/settings/units/import-default").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/units/groups"));

        assertThat(groupNames()).containsAll(STANDARD_GROUPS);
    }

    @Test
    void importDefault_idempotent_secondPostNoDuplicates() throws Exception {
        mockMvc.perform(post("/settings/units/import-default").with(csrf()))
                .andExpect(status().is3xxRedirection());
        int afterFirst = unitService.groups().size();

        mockMvc.perform(post("/settings/units/import-default").with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Ном бўйича idempotent - иккинчи POST дубл гуруҳ қўшмайди
        assertThat(unitService.groups()).hasSize(afterFirst);
        assertThat(groupNames()).containsAll(STANDARD_GROUPS);
    }

    @Test
    void importDefault_withoutCsrf_redirectsToExpired() throws Exception {
        mockMvc.perform(post("/settings/units/import-default"))
                .andExpect(redirectedUrl("/login?expired"));

        // POST рад этилди - гуруҳлар яратилмади
        assertThat(groupNames()).doesNotContainAnyElementsOf(STANDARD_GROUPS);
    }

    @Test
    void importDefault_viewer_forbidden() throws Exception {
        var viewer = TestRoles.as("viewer", UserRole.VIEWER_AUDITOR);
        mockMvc.perform(post("/settings/units/import-default").with(viewer).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(groupNames()).doesNotContainAnyElementsOf(STANDARD_GROUPS);
    }
}
