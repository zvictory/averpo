package com.averpo.erp.security.config;

import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Биринчи ишга туширишда admin фойдаланувчисини яратади - seed
 * changeset'да парол hash сақламаслик учун (алмаштириш имконсиз бўлиб
 * қоларди). Парол env'дан олинади.
 *
 * <p>Fail-safe мантиқ: парол берилмаганда default парол ФАҚАТ аниқ
 * dev белгиси бор муҳитда ишлайди - dev/test профили фаол ёки
 * AVERPO_ALLOW_DEV_ADMIN=true. Бошқа ҳар қандай муҳит (жумладан
 * профилсиз deploy) АТАЙЛАБ start бўлмайди: молия тизими default
 * admin/admin билан интернетга чиқиб қолиши P1 хавф, «prod профилини
 * унутиб қўйиш» бу ҳимояни айланиб ўтмаслиги керак. Бу business rule
 * эмас, deploy конфигурация хатоси - шунинг учун BusinessRuleException
 * эмас, IllegalStateException билан бутун boot тўхтатилади.
 *
 * @author Zafar
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    /** Admin фойдаланувчисининг логини - login autofill ҳам шуни ишлатади. */
    public static final String ADMIN_USERNAME = "admin";

    /** Dev муҳитда env берилмаганда ишлатиладиган default парол.
     * Public: dev профилида login формаси шу қийматни автоматик
     * тўлдиради (LoginController) - иккита жойда ёзилмасин.
     * Production'да AVERPO_ADMIN_PASSWORD env МАЖБУРИЙ - бу қиймат
     * фақат dev/test профилида амал қилади ({@link #devEnvironment()}). */
    public static final String DEV_DEFAULT_PASSWORD = "AverpoDev!2026";

    /** Фойдаланувчилар репозиторийси. */
    private final AppUserRepository repository;

    /** Парол hash'лагич. */
    private final PasswordEncoder passwordEncoder;

    /** Профиль/env текшируви учун - dev белгисисиз default парол йўқ. */
    private final Environment environment;

    /** Admin пароли - production'да env орқали МАЖБУРИЙ. Бўш default:
     * "берилмаган"ни аниқлаш учун (default қийматни паролдан фарқлаймиз). */
    @Value("${AVERPO_ADMIN_PASSWORD:}")
    private String adminPassword;

    /** Жадвал бўш бўлса admin яратади; dev белгисисиз паролсиз boot тўхтайди. */
    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        String password = adminPassword;
        if (password == null || password.isBlank()) {
            if (!devEnvironment()) {
                throw new IllegalStateException(
                        "AVERPO_ADMIN_PASSWORD env берилмаган. Default admin пароли "
                        + "фақат dev/test профилида ёки AVERPO_ALLOW_DEV_ADMIN=true "
                        + "билан ишлайди - production'да env ўзгарувчисини бериб "
                        + "қайта ишга туширинг.");
            }
            password = DEV_DEFAULT_PASSWORD;
            log.warn("ДИҚҚАТ: admin фойдаланувчиси DEV DEFAULT парол билан яратилди. "
                    + "Production'da AVERPO_ADMIN_PASSWORD env бериш ШАРТ!");
        } else {
            log.info("Admin фойдаланувчиси яратилди (парол env'дан)");
        }
        // Роль тизимида (user-roles.md) биринчи фойдаланувчи доим
        // SUPER_ADMIN - акс ҳолда SETTINGS/USERS'га ҳеч ким кира олмасди
        repository.save(new AppUser(ADMIN_USERNAME,
                passwordEncoder.encode(password),
                "Administrator", UserRole.SUPER_ADMIN));
    }

    /**
     * Муҳит аниқ dev деб белгиланганми: dev/test профили фаол ёки
     * AVERPO_ALLOW_DEV_ADMIN=true env бор. Рўйхат атайлаб «оқ» -
     * номаълум/профилсиз муҳит доим production деб қаралади (fail-safe).
     */
    private boolean devEnvironment() {
        return environment.acceptsProfiles(Profiles.of("dev", "test"))
                || "true".equalsIgnoreCase(environment.getProperty("AVERPO_ALLOW_DEV_ADMIN"));
    }
}
