package com.averpo.erp.config;

import com.averpo.erp.AverpoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ProdDatasourceGuard тестлари (Eldor-001): prod профилда заиф/йўқ DB
 * пароли билан boot тўхташи, dev оқими эса тегилмагани текширилади.
 * Тўлиқ boot тести @SpringBootTest эмас - guard контекст кўтарилишини
 * атайлаб йиқитади, кэшланадиган контекст йўқ.
 */
class ProdDatasourceGuardTest {

    /**
     * prod профил + default парол - тўлиқ boot BFPP фазасида тўхтайди,
     * базага биронта уланиш бўлмайди (Liquibase bean'и яратилмайди -
     * шунинг учун бу тест prod URL'га қарамай хавфсиз). Парол атайлаб
     * аргумент билан default'га қотирилади: машинада тасодифан
     * DB_PASSWORD env бўлса ҳам тест ҳермет қолади.
     */
    @Test
    void prodProfile_defaultDbPassword_bootFailsFast() {
        SpringApplication app = new SpringApplication(AverpoApplication.class);
        app.setAdditionalProfiles("prod");
        app.setWebApplicationType(WebApplicationType.NONE);

        Throwable thrown = catchThrowable(() ->
                app.run("--spring.datasource.password="
                        + ProdDatasourceGuard.DEV_DEFAULT_DB_PASSWORD));

        assertThat(thrown).isNotNull();
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    /** MockEnvironment билан созланган guard (unit текширувлар учун). */
    private ProdDatasourceGuard guardWith(MockEnvironment env) {
        ProdDatasourceGuard guard = new ProdDatasourceGuard();
        guard.setEnvironment(env);
        return guard;
    }

    /** prod'да парол умуман берилмаган (бўш) ҳолат ҳам рад этилади. */
    @Test
    void prodProfile_blankPassword_rejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "");

        assertThatThrownBy(() -> guardWith(env)
                .postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    /** prod'да кучли парол берилган - guard индамай ўтказади. */
    @Test
    void prodProfile_strongPassword_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "kuchli-maxfiy-parol-2026");

        assertThatCode(() -> guardWith(env)
                .postProcessBeanFactory(new DefaultListableBeanFactory()))
                .doesNotThrowAnyException();
    }

    /** Профилсиз (локал dev) старт default парол билан аввалгидек юради. */
    @Test
    void noProfile_devDefaultPassword_allowed() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.password",
                ProdDatasourceGuard.DEV_DEFAULT_DB_PASSWORD);

        assertThatCode(() -> guardWith(env)
                .postProcessBeanFactory(new DefaultListableBeanFactory()))
                .doesNotThrowAnyException();
    }
}
