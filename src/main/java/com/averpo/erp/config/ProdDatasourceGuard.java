package com.averpo.erp.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Production DB пароли guard'и (SEC-001): prod профили фаол бўлиб
 * DB_PASSWORD берилмаган (application.yml default'ига тушиб қолган)
 * ёки айнан ўша dev default'ига тенг бўлса - boot ТЎХТАЙДИ. Акс ҳолда
 * «prod'да env беришни унутиш» сценарийсида молия тизими git'даги очиқ
 * заиф парол билан жимгина ишлаб қоларди.
 *
 * <p>AdminUserInitializer шаблони (deploy конфигурация хатоси =
 * IllegalStateException, тушунарли хабар билан), лекин ҳаёт цикли
 * атайлаб эртароқ: BeanFactoryPostProcessor фазаси ҳар қандай bean
 * (datasource, Liquibase) яратилишидан ОЛДИН келади - хато парол
 * билан базага биронта ҳам уланиш бўлмайди.
 *
 * <p>AdminUserInitializer'дан фарқ: у профилсиз муҳитни ҳам production
 * деб қарайди, бу guard эса ФАҚАТ prod профилида ишлайди - профилсиз
 * локал старт (dev оқими) аввалгидек default DB пароли билан юраверади
 * (арбитр кўлами, SEC-001).
 */
@Component
public class ProdDatasourceGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    /** application.yml'даги dev default DB пароли - prod'да тақиқланган
     * қиймат сифатида солиштирилади. */
    static final String DEV_DEFAULT_DB_PASSWORD = "averpo";

    /** Профиль ва property текшируви учун. */
    private Environment environment;

    /** Constructor injection BFPP фазасида ишламайди (autowiring BPP'си
     * ҳали рўйхатда эмас) - Environment шу Aware callback'дан келади. */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /** prod профилда паролни текширади; муаммо бўлса boot тўхтайди. */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        // Резолв қилинган қиймат текширилади (${DB_PASSWORD:default}):
        // env берилмаса default'га тушади - иккала ҳолат ҳам шу ерда
        String password = environment.getProperty("spring.datasource.password");
        if (password == null || password.isBlank()
                || DEV_DEFAULT_DB_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "DB_PASSWORD env берилмаган ёки dev default қийматига тенг. "
                    + "Git'даги default парол фақат локал dev учун - prod профилида "
                    + "кучли DB_PASSWORD env ўзгарувчисини бериб қайта ишга туширинг.");
        }
    }
}
