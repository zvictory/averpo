package com.averpo.erp.security.config;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.security.domain.Capability;
import com.averpo.erp.security.domain.RolePermissions;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Хавфсизлик конфигурацияси: form login + CSRF (default ёқиқ).
 *
 * <p>Барча саҳифалар авторизация талаб қилади - фақат login, статик
 * asset'лар ва error очиқ. CSRF токени ҳар POST формада hidden input
 * сифатида юборилади (GlobalModelAttributes -> шаблонлар).
 *
 * <p>Роль модели (docs/modules/user-roles.md, DEC-092): рухсат
 * РОЛГА эмас, СОҲАга текширилади - URL'лар {@link UrlPermissionMap}
 * орқали соҳаларга мапланади, ҳар соҳада GET={@code <СОҲА>_VIEW},
 * ёзувчи метод (POST)={@code <СОҲА>_EDIT} authority талаб қилинади.
 * Authority'ларни роль учун {@link RolePermissions} матрицаси беради.
 * Янги роль қўшиш бу файлни ЎЗГАРТИРМАЙДИ - фақат матрицага қатор
 * қўшилади.
 *
 * <p>{@code @EnableMethodSecurity} - иккинчи қатлам: URL'дан ташқари
 * сезгир амаллар (@PreAuthorize: PERIOD_CLOSE, factory reset) ва
 * service гаровлари (BR-USR-011/012) учун.
 *
 * <p>DEC-096: {@code exceptionHandling.accessDeniedHandler} -
 * {@link CsrfAwareAccessDeniedHandler} CSRF радини (эскирган сессия)
 * {@code /login?expired} га буради, қолган 403'лар default'да қолади
 * (CsrfConfigurer шу handler'ни CsrfFilter'га ҳам улайди).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Remember-me hash калити манбаи (DEC-141): restart'да token
     * сақланиши учун БАРҚАРОР сир - prod'да {@code AVERPO_SECRET_KEY}
     * (deploy env, SecretCrypto билан бир хил сир). Тасодифий бўлса
     * restart token'ни бекор қиларди, hardcode prod сирини ошкор қиларди
     * - иккови ҳам ТАҚИҚ (танлов {@link #rememberMeKey} да).
     */
    @Value("${AVERPO_SECRET_KEY:}")
    private String secretKey;

    /**
     * Локал dev/test учун барқарор remember-me fallback (SecretCrypto
     * {@code DEV_KEY} нақши): env берилмаганда бу калит - bootRun
     * restart'ида ҳам token яшайди. Ошкор, лекин prod'да env МАЖБУРИЙ
     * уни алмаштиради; профилсиз/production муҳитда умуман ишламайди.
     */
    private static final String DEV_REMEMBER_ME_KEY = "averpo-dev-remember-me-key";

    /** Асосий filter chain: авторизация қоидалари + login/logout. */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SetupRedirectSuccessHandler setupRedirectSuccessHandler,
                                           AuditLogService auditLogService,
                                           Environment environment) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    // Static ресурслар очиқ: login саҳифаси ҳам CSS/шрифт/JS
                    // ишлатади (аноним ҳолат) - уларда маълумот йўқ (DEC-116:
                    // /css ва /fonts Tailwind пайплайн + Inter вендоринги учун;
                    // DEC-143: /img - login вендор логоси, аноним юкланади)
                    auth.requestMatchers("/login", "/vendor/**", "/js/**", "/css/**",
                            "/fonts/**", "/img/**", "/favicon.svg", "/error").permitAll();
                    // Telegram webhook (DEC-138): Telegram аутентификация
                    // қилмайди - permitAll шарт. Ҳимоя X-Telegram-Bot-Api-Secret-
                    // Token header'ида (registrar яратган сир, constant-time
                    // таққос TelegramService'да). POST-catchall'дан ОЛДИН туриши
                    // шарт (акс ҳолда соҳа EDIT талабига тушиб 403 берарди)
                    auth.requestMatchers(HttpMethod.POST, "/telegram/webhook").permitAll();
                    // Ҳар роль logout қила олиши шарт - соҳа қоидаларидан олдин
                    auth.requestMatchers(HttpMethod.POST, "/logout").authenticated();
                    // Ҳар роль (VIEWER_AUDITOR ҳам) ЎЗ профилини бошқаради:
                    // парол, шахсий майдонлар, аватар (DEC-101). Бу POST'лар
                    // UrlPermissionMap'га КИРМАЙДИ (соҳасиз /profile) - шунинг
                    // учун АНИҚ authenticated (092 ТУЗОҚИ: акс ҳолда пастдаги
                    // POST-catchall уларни соҳа EDIT талабига ташлаб, view-only
                    // роль ўз профилини сақлай олмасди)
                    // DEC-103: Telegram улаш/узиш ҳам ЎЗ профили амали -
                    // ўша рўйхатда (плагин гейти контроллерда: ўчиқда 404)
                    auth.requestMatchers(HttpMethod.POST, "/profile/password",
                            "/profile", "/profile/image", "/profile/image/delete",
                            "/profile/telegram/link", "/profile/telegram/unlink")
                            .authenticated();
                    // PERIOD_CLOSE имконияти (SUPER_ADMIN + CHIEF_ACCOUNTANT):
                    // давр ёпилиш санаси - /settings/** (SETTINGS) қоидасидан
                    // ОЛДИН туриши ШАРТ, акс ҳолда CHIEF унга ета олмайди
                    auth.requestMatchers("/settings/closing-date")
                            .hasAuthority(Capability.PERIOD_CLOSE.name());
                    // URL → соҳа қоидалари. Тартиб UrlPermissionMap'да қатъий
                    // (у ерда ⚠ изоҳланган); ҳар соҳада аввал ёзувчи метод
                    // (EDIT), кейин қолгани (VIEW) - тескариси POST'ни VIEW
                    // билан ўтказиб юборар эди
                    for (UrlPermissionMap.Rule rule : UrlPermissionMap.RULES) {
                        auth.requestMatchers(HttpMethod.POST, rule.patternArray())
                                .hasAuthority(RolePermissions.editAuthority(rule.area()));
                        auth.requestMatchers(rule.patternArray())
                                .hasAuthority(RolePermissions.viewAuthority(rule.area()));
                    }
                    // Эски «VIEWER ҳар POST'дан блокланган» қоидасининг мероси:
                    // харитага КИРМАГАН POST (масалан /attachments) камида битта
                    // соҳада EDIT талаб қилади - тоза view-only роллар учун
                    // ёзувчи йўл умуман қолмайди
                    auth.requestMatchers(HttpMethod.POST, "/**")
                            .hasAnyAuthority(RolePermissions.allEditAuthorities());
                    auth.anyRequest().authenticated();
                })
                // Telegram webhook (DEC-138): Telegram CSRF токен юбормайди -
                // ФАҚАТ шу битта йўлга CSRF ўчади (ҳимоя secret_token header'ида,
                // constant-time). Бошқа ҳамма POST CSRF ҳимоясида қолади.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/telegram/webhook"))
                // CSRF 403 UX (DEC-096): сессия муддати тугаб токен эскирса
                // Whitelabel 403 ўрнига /login?expired. Соҳа-даража радлари
                // (092) default 403 → /error (ErrorController) сақланади.
                .exceptionHandling(ex -> ex.accessDeniedHandler(new CsrfAwareAccessDeniedHandler()))
                // PDF preview (DEC-128): default X-Frame-Options: DENY ЎЗ
                // саҳифамиздаги иловалар модали <iframe>'ини ҳам блоклар эди.
                // sameOrigin - фақат ўз доменимиз frame қила олади: PDF модали
                // ишлайди, ташқи сайт clickjacking'и аввалгидек тўсилади.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .formLogin(form -> form
                        .loginPage("/login")
                        // Онбординг (DEC-056): SUPER_ADMIN янги ўрнатишда
                        // /settings?setup=1 га - қолган ҳолда saved request сақланади
                        .successHandler(setupRedirectSuccessHandler)
                        // Lockout (BR-USR-009) алоҳида хабар олади - ?error
                        // умумий хабари username enumeration бермай қолаверади
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(request.getContextPath()
                                        + (exception instanceof org.springframework.security.authentication.LockedException
                                                ? "/login?locked" : "/login?error")))
                        .permitAll())
                // Remember-me (DEC-141): 14 кун, hash-based
                // (TokenBasedRememberMeServices, Spring default) - token
                // парол hash'ига боғлиқ, шунга logout ёки парол ўзгаришида
                // авто бекор бўлади (алоҳида changeset/DB шарт эмас). Key
                // БАРҚАРОР сир (rememberMeKey): restart'да token сақланади.
                // name="remember-me" Spring default параметри - login
                // формасидаги checkbox шу ном билан юборади (login.jte)
                .rememberMe(rm -> rm
                        .key(rememberMeKey(environment))
                        .tokenValiditySeconds(14 * 24 * 60 * 60))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // Аудит LOGOUT ёзуви (DEC-062): security → audit
                        // боғлиқлик рухсатли (LoginAttemptListener прецеденти).
                        // IP/UA ва CF header'лари (DEC-091) request'дан аниқ
                        // узатилади - handler RequestContextHolder'га таянмайди
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                auditLogService.record(AuditEventType.LOGOUT,
                                        authentication.getName(), null, null, null,
                                        request.getRemoteAddr(),
                                        request.getHeader("User-Agent"),
                                        request.getHeader(AuditLogService.CF_PSEUDO_IPV4),
                                        request.getHeader(AuditLogService.CF_IP_COUNTRY));
                            }
                            response.sendRedirect(request.getContextPath() + "/login?logout");
                        }));
        return http.build();
    }

    /**
     * Онбординг йўналтириши (DEC-056): янги ўрнатишда SUPER_ADMIN'ни
     * компания созламаларига олиб боради. Shared service олинади (security ->
     * shared рухсат этилган йўналиш - shared ҳеч кимга боғлиқ эмас).
     */
    @Bean
    public SetupRedirectSuccessHandler setupRedirectSuccessHandler(
            CompanySettingsService settingsService,
            com.averpo.erp.security.service.UserService userService) {
        return new SetupRedirectSuccessHandler(settingsService, userService);
    }

    /** Парол hash'лаш - bcrypt (соҳа стандарти). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Глобал {@link AuthenticationManager}'ни bean сифатида очади - заводга
     * қайтариш (factory-reset.md) оқими жорий admin паролини қайта текшириш
     * учун ишлатади (BR-RST-001). Login filter'и билан бир хил
     * DaoAuthenticationProvider занжиридан ўтади: нотўғри парол
     * BadCredentials беради, LoginAttemptListener орқали lockout счётчигига
     * ҳам киради (danger оқимини brute-force қилишга қарши қўшимча ҳимоя).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Remember-me hash калити (DEC-141) - restart'да token сақланиши
     * учун барқарор бўлиши ШАРТ.
     *
     * <p>Prod'да {@link #secretKey} ({@code AVERPO_SECRET_KEY}, deploy
     * env). Env бўш бўлса аниқ dev/test профилда {@link #DEV_REMEMBER_ME_KEY}
     * (локал bootRun ишласин), акс ҳолда тасодифий калит - production'да
     * env етишса ошкор dev калит ИШЛАТИЛМАСИН деб (SecretCrypto fail-safe
     * нақши: номаълум/профилсиз муҳит доим production). Тасодифийда
     * remember-me restart'да бекор бўлади, лекин сир ошкор бўлмайди.
     */
    private String rememberMeKey(Environment environment) {
        if (secretKey != null && !secretKey.isBlank()) {
            return secretKey;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equals(profile) || "test".equals(profile)) {
                return DEV_REMEMBER_ME_KEY;
            }
        }
        return java.util.UUID.randomUUID().toString();
    }
}
