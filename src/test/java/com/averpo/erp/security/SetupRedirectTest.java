package com.averpo.erp.security;

import com.averpo.erp.security.config.SetupRedirectSuccessHandler;
import com.averpo.erp.security.domain.RolePermissions;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.repo.AppUserRepository;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Arbitr-056: биринчи киришда онбординг йўналтириши.
 *
 * <p>Success handler'ни тўғридан-тўғри юритамиз (ҳақиқий bean + service +
 * test база) - seed фойдаланувчисиз, чунки текширилаётган ягона мантиқ
 * «роль ADMIN ми ва setupDone false ми». Redirect манзили response'дан
 * ўқилади. Онбординг тугаган/ADMIN эмас ҳолда одатий Saved Request оқими
 * ("/") сақланиши шарт.
 *
 * <p>Botir-053: тўғридан чақирувли тестлар SecurityConfig'даги
 * {@code .successHandler} УЛАНИШИни босмайди - уланиш тушиб қолса handler
 * bean тирик, тестлар яшил, лекин онбординг ўлик бўларди. Шунга пастда
 * битта тест ҳақиқий filter chain орқали (MockMvc formLogin) киради.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SetupRedirectTest {

    /** Текширилаётган ҳақиқий handler bean (SecurityConfig'да уланган). */
    @Autowired SetupRedirectSuccessHandler handler;

    /** Онбординг ҳолатини (setupDone) созлаш/ўқиш учун. */
    @Autowired CompanySettingsService settingsService;

    /** Filter chain'ли MockMvc қуриш учун (Botir-053 тести). */
    @Autowired WebApplicationContext context;

    /** Ҳақиқий bcrypt паролли admin яратиш - formLogin занжири учун. */
    @Autowired UserService userService;

    /** create'дан кейин must_change'ни тушириш учун (setup redirect тести, рефайнмент). */
    @Autowired AppUserRepository repository;

    /**
     * Берилган роль билан кириш симуляцияси - handler қайси манзилга
     * redirect қилганини қайтаради (session/saved request йўқ).
     * Authority'лар продакшн матрицасидан (Arbitr-092) - handler энди
     * роль номини эмас, SETTINGS_EDIT authority'сини текширади.
     */
    private String redirectFor(UserRole role) throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "u", "p", RolePermissions.authorities(role));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, auth);
        return response.getRedirectedUrl();
    }

    @Test
    void admin_setupNotDone_redirectsToSetup() throws Exception {
        // Янги (бўш) ўрнатиш: default setupDone=false
        assertThat(settingsService.isSetupDone()).isFalse();
        assertThat(redirectFor(UserRole.SUPER_ADMIN)).isEqualTo("/settings?setup=1");
    }

    @Test
    void admin_setupDone_redirectsToDefault() throws Exception {
        settingsService.update("Тест компания", "UZS", "Asia/Tashkent", null, null);
        assertThat(settingsService.isSetupDone()).isTrue();
        // Онбординг тугаган - одатий оқим (target йўқ → "/")
        assertThat(redirectFor(UserRole.SUPER_ADMIN)).isEqualTo("/");
    }

    @Test
    void accountant_setupNotDone_notRedirectedToSetup() throws Exception {
        // ACCOUNTANT созлай олмайди - setupDone=false бўлса ҳам одатий оқим
        assertThat(settingsService.isSetupDone()).isFalse();
        assertThat(redirectFor(UserRole.ACCOUNTANT)).isEqualTo("/");
    }

    @Test
    void formLogin_viaFilterChain_redirectsBySetupState() throws Exception {
        // Botir-053: handler мантиғи эмас, УЛАНИШ синови - ҳақиқий POST /login
        // DaoAuthenticationProvider (bcrypt) + SecurityConfig'даги successHandler
        // занжиридан тўлиқ ўтади; уланиш узилса айнан шу тест қизаради
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        userService.create("setupadm085", "Созлаш админи", UserRole.SUPER_ADMIN, "SetupPass123");
        // Рефайнмент: create → must_change_password=true; бу тест ФАҚАТ setup
        // redirect'ни синайди (must_change /profile'га олиб кетмасин), шунга тушамиз
        repository.findByUsername("setupadm085").orElseThrow().clearPasswordChangeRequirement();

        // Янги ўрнатиш (setupDone=false): кириш онбординг манзилига тушади
        assertThat(settingsService.isSetupDone()).isFalse();
        mockMvc.perform(formLogin().user("setupadm085").password("SetupPass123"))
                .andExpect(redirectedUrl("/settings?setup=1"));

        // Созламалар тўлдирилгандан кейин иккинчи кириш - одатий "/" оқими
        settingsService.update("Тест компания 085", "UZS", "Asia/Tashkent", null, null);
        mockMvc.perform(formLogin().user("setupadm085").password("SetupPass123"))
                .andExpect(redirectedUrl("/"));
    }
}
