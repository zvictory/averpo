package com.averpo.erp.plugins.telegram;

import com.averpo.erp.plugins.telegram.service.TelegramPoller;
import com.averpo.erp.plugins.telegram.service.TelegramWebhookRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Режим bean'ларининг профил тақсими тести (DEC-138): тест
 * профилда ({@code test}) НА poller (у {@code @Profile("dev")}), НА
 * webhook registrar (у {@code @Profile("!dev & !test")}) яратилмайди -
 * тест тармоққа умуман тегмайди ва детерминистик қолади.
 *
 * <p>Prod (профилсиз) → registrar, дев ({@code dev}) → poller: бу
 * ажралишни аннотация ва профил ифодаси кафолатлайди; шу тест
 * иккисининг ҳам тест контекстида ЙЎҚлигини қотиради (келажакда
 * биров профилни ўзгартирса тест дарҳол қизаради).
 */
@SpringBootTest
@ActiveProfiles("test")
class TelegramProfileWiringTest {

    @Autowired ApplicationContext context;

    /** Тест профилда poller bean'и умуман йўқ (@Profile("dev")). */
    @Test
    void pollerAbsentInTestProfile() {
        assertThat(context.getBeanNamesForType(TelegramPoller.class)).isEmpty();
    }

    /** Тест профилда registrar bean'и умуман йўқ (@Profile("!dev & !test")). */
    @Test
    void webhookRegistrarAbsentInTestProfile() {
        assertThat(context.getBeanNamesForType(TelegramWebhookRegistrar.class)).isEmpty();
    }
}
