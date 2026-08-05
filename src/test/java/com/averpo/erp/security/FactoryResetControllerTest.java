package com.averpo.erp.security;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Заводга қайтариш оқими web тести (factory-reset.md): роль гарови
 * (ACCOUNTANT 403), нотўғри пароль (BR-RST-001, ҳеч нарса ўчмайди),
 * тўғри пароль (2-босқичга ўтиш) ва якуний confirm занжири (TST-050):
 * session гарови, тасдиқ матни (BR-RST-002) ва тўлиқ занжирда ҳақиқий
 * reset'нинг controller сими орқали ишлаши. Reset'нинг тўлиқ тозалаш
 * кафолати {@link com.averpo.erp.shared.FactoryResetServiceTest}'да.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FactoryResetControllerTest {

    @Autowired
    private WebApplicationContext context;

    /** Ҳақиқий admin яратиш (AuthenticationManager текшируви учун). */
    @Autowired
    private UserService userService;

    /** «Ҳеч нарса ўчмади» тасдиғи учун хом SQL. */
    @Autowired
    private JdbcClient jdbcClient;

    /** Тасдиқ матни учун ҳақиқий компания номини олиш (BR-RST-002). */
    @Autowired
    private CompanySettingsService settingsService;

    /** Security filter chain уланган MockMvc. */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "acc")
    void step1_accountantForbidden() throws Exception {
        // /settings/** = SETTINGS соҳаси (SUPER_ADMIN) → ACCOUNTANT 403 (GET ва POST)
        mockMvc.perform(get("/settings/reset"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/reset").with(csrf()).param("password", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockRole(username = "rstpwadmin")
    void wrongPassword_showsBrRst001_andDeletesNothing() throws Exception {
        // Ҳақиқий admin (bcrypt) - AuthenticationManager текшируви учун
        userService.create("rstpwadmin", "Reset Admin", UserRole.SUPER_ADMIN, "CorrectPass123");
        // Ўчмаслиги текшириладиган иш маълумоти
        jdbcClient.sql("INSERT INTO contact (id, type, display_name) "
                + "VALUES (?, 'CUSTOMER', 'Ўчмаслиги керак')").param(UUID.randomUUID()).update();

        mockMvc.perform(post("/settings/reset").with(csrf())
                        .param("password", "NotThePassword"))
                .andExpect(status().isOk())
                // BR-RST-001 хабари 1-босқичда кўринади (код ёнида)
                .andExpect(content().string(containsString("BR-RST-001")));

        // Reset умуман бошланмади - контакт жойида
        assertThat(jdbcClient.sql("SELECT count(*) FROM contact WHERE display_name = 'Ўчмаслиги керак'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @WithMockRole(username = "rstpwadmin")
    void correctPassword_advancesToStep2() throws Exception {
        userService.create("rstpwadmin", "Reset Admin", UserRole.SUPER_ADMIN, "CorrectPass123");

        mockMvc.perform(post("/settings/reset").with(csrf())
                        .param("password", "CorrectPass123"))
                .andExpect(status().isOk())
                // 2-босқич: якуний тасдиқ саҳифаси
                .andExpect(content().string(containsString("Қарорингиз қатъийми")));
    }

    @Test
    @WithMockRole(username = "rstcnfadmin")
    void confirm_wrongName_showsBrRst002_andDeletesNothing() throws Exception {
        // Пароль босқичи ўтган, лекин тасдиқ матни компания номига мос эмас -
        // BR-RST-002 билан 2-босқичда қоламиз, reset умуман бошланмайди (TST-050)
        userService.create("rstcnfadmin", "Reset Admin", UserRole.SUPER_ADMIN, "CorrectPass123");
        jdbcClient.sql("INSERT INTO contact (id, type, display_name) "
                + "VALUES (?, 'CUSTOMER', 'Нотўғри тасдиқда ўчмасин')")
                .param(UUID.randomUUID()).update();
        MockHttpSession session = passwordStepSession("CorrectPass123");

        mockMvc.perform(post("/settings/reset/confirm").session(session).with(csrf())
                        .param("confirmName", "Бутунлай бошқа ном"))
                .andExpect(status().isOk())
                // BR-RST-002 хабари 2-босқичда кўринади (код ёнида)
                .andExpect(content().string(containsString("BR-RST-002")));

        // Контакт жойида - TRUNCATE ишга тушмаган
        assertThat(jdbcClient.sql("SELECT count(*) FROM contact "
                        + "WHERE display_name = 'Нотўғри тасдиқда ўчмасин'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @WithMockRole(username = "rstcnfadmin")
    void confirm_withoutPasswordStep_redirectsBackToStep1_andDeletesNothing() throws Exception {
        // Session маркерисиз (пароль босқичи ўтилмаган) тўғридан-тўғри POST -
        // ҳатто ТЎҒРИ компания номи билан ҳам 1-босқичга қайтарилади (TST-050)
        jdbcClient.sql("INSERT INTO contact (id, type, display_name) "
                + "VALUES (?, 'CUSTOMER', 'Маркерсиз ўчмасин')")
                .param(UUID.randomUUID()).update();

        mockMvc.perform(post("/settings/reset/confirm").with(csrf())
                        .param("confirmName", settingsService.get().getName()))
                .andExpect(redirectedUrl("/settings/reset"));

        // Ҳеч нарса ўчмаган
        assertThat(jdbcClient.sql("SELECT count(*) FROM contact "
                        + "WHERE display_name = 'Маркерсиз ўчмасин'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @WithMockRole(username = "rstcnfadmin")
    void confirm_correctNameAfterPasswordStep_runsReset_andRedirectsToSetup() throws Exception {
        // Тўлиқ занжир: пароль босқичи + айнан компания номи → ҳақиқий reset
        // (TRUNCATE) controller сими орқали ишлайди ва setup'га йўналтиради.
        // Изоляция - FactoryResetServiceTest қолипи: @Transactional rollback
        // (TRUNCATE Postgres'да транзакцион), кейинги тестларга из қолмайди.
        userService.create("rstcnfadmin", "Reset Admin", UserRole.SUPER_ADMIN, "CorrectPass123");
        jdbcClient.sql("INSERT INTO contact (id, type, display_name) "
                + "VALUES (?, 'CUSTOMER', 'Тасдиқда ўчиши шарт')")
                .param(UUID.randomUUID()).update();
        String companyName = settingsService.get().getName();
        MockHttpSession session = passwordStepSession("CorrectPass123");
        // Ўчиш кейин исботли бўлиши учун аввал борлиги қайд этилади
        assertThat(count("contact")).isEqualTo(1L);

        mockMvc.perform(post("/settings/reset/confirm").session(session).with(csrf())
                        .param("confirmName", companyName))
                .andExpect(redirectedUrl("/settings?setup=1"));

        // Иш маълумоти ўчди; фақат reset қилган admin қолгани - adminId
        // controller'да authentication'дан тўғри ечилганининг исботи
        assertThat(count("contact")).isZero();
        assertThat(jdbcClient.sql("SELECT username FROM app_user").query(String.class).list())
                .containsExactly("rstcnfadmin");
    }

    /**
     * 1-босқичдан ҳақиқий ўтиш: тўғри парол POST қилинади ва controller
     * пароль маркерини қўйган сессия қайтарилади. Маркер attribute номини
     * тестда такрорламаймиз - икки босқич айнан бир калитда ишлашини ҳам
     * шу йўл ўзи тасдиқлайди.
     */
    private MockHttpSession passwordStepSession(String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/settings/reset").with(csrf())
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    /** Жадвал сатрлари сони (жадвал номи - код константаси, инъекция йўқ). */
    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
