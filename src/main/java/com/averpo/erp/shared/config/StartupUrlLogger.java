package com.averpo.erp.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Илова кўтарилиб битгач лог охирида кириш URL'ини аниқ кўрсатади
 * (Arbitr-082): «Илова тайёр: http://localhost:8080». Spring'нинг
 * стандарт «Started ... in N seconds» сатридан кейин фойдаланувчи
 * қайси манзилга киришни бир қарашда кўрмас эди.
 *
 * <p><b>Нега икки event:</b> порт битта event'да маълум, «ҳақиқий тайёр»
 * нуқта эса бошқасида - иккови ажралган:
 * <ul>
 *   <li>{@link WebServerInitializedEvent} - web server кўтарилиб, ХАҚИҚИЙ
 *     тингланаётган портни беради ({@code getWebServer().getPort()}).
 *     Конфигдаги {@code server.port=0} (random порт) ёки {@code SERVER_PORT}
 *     env ҳолида ҳам асл порт шу ердан келади - property'дан ўқиш
 *     нотўғри бўларди.</li>
 *   <li>{@link ApplicationReadyEvent} - барча {@code ApplicationRunner}'лар
 *     (масалан default chart init, admin seed) ишлаб бўлгач отилади:
 *     мана шу «ростдан тайёр» нуқта, URL шу ерда босилади.</li>
 * </ul>
 *
 * <p>MockMvc/{@code @SpringBootTest} (mock web environment) да ҳақиқий web
 * server кўтарилмайди - {@link WebServerInitializedEvent} УМУМАН отилмайди,
 * {@code port} 0 бўлиб қолади ва {@link ApplicationReadyEvent}'даги
 * қоровул жимгина чиқиб кетади. Шунинг учун тестларда лог шовқини йўқ,
 * {@code @Profile} гарови ҳам керак эмас.
 *
 * <p>Actuator management порти алоҳида бўлса иккинчи
 * {@link WebServerInitializedEvent} «management» namespace билан отилади -
 * уни четлаб, фойдаланувчи кирадиган асосий сервер портини сақлаймиз
 * (ҳозир management порт йўқ, бу келажак учун гаров).
 */
@Component
@Slf4j
public class StartupUrlLogger {

    /**
     * Web server кўтарилганда ёзилиб, {@code ApplicationReadyEvent}'да
     * ўқилади: иккала event ҳам startup'даги битта {@code main} thread'да
     * кетма-кет боради, шунга алоҳида синхронизация керак эмас. Web
     * server умуман кўтарилмаган ҳолда (MockMvc) 0 бўлиб қолади - бу
     * «лог қилинмасин» сигнали.
     */
    private int port;

    /**
     * Контекст-path (масалан {@code /erp}) - конфигда берилса URL'га
     * қўшилади; одатда бўш (илдиз). Порт билан бирга шу event'да олинади.
     */
    private String contextPath = "";

    /**
     * Асосий web server кўтарилди - портни (ва контекст-path'ни) сақлайди.
     * Management (actuator) namespace'ли event эса эътиборсиз: URL асосий
     * серверники бўлиши керак.
     */
    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        String namespace = event.getApplicationContext().getServerNamespace();
        if (namespace != null && !namespace.isBlank()) {
            return; // management/actuator порти - фойдаланувчи URL'и эмас
        }
        this.port = event.getWebServer().getPort();
        String configured = event.getApplicationContext().getEnvironment()
                .getProperty("server.servlet.context-path");
        this.contextPath = configured == null ? "" : configured.strip();
    }

    /**
     * Ҳамма runner'лардан кейин - лог охирида тайёр URL'ни босади.
     * Web server кўтарилмаган контекстда (test) {@code port==0} - индамай
     * чиқади.
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (port == 0) {
            return; // web server йўқ (MockMvc/non-web) - шовқин қилмаймиз
        }
        log.info("Илова тайёр: http://localhost:{}{}", port, contextPath);
    }
}
