package com.averpo.erp.plugins.telegram.service;

import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginToggledEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Webhook registrar «қачон» мантиғининг unit тести (Arbitr-138) -
 * Spring контекстисиз, мок {@link TelegramService} билан: старт ва
 * event'лар тўғри service методига уланадими. Ишлов мантиғи
 * (registerWebhookIfReady/remove) TelegramServiceTest'да.
 */
class TelegramWebhookRegistrarTest {

    /** Старт: registerWebhookIfReady чақирилади (fail-safe - шартни service текширади). */
    @Test
    void run_registersOnStartup() {
        TelegramService service = mock(TelegramService.class);
        new TelegramWebhookRegistrar(service).run(null);

        verify(service).registerWebhookIfReady();
    }

    /** TELEGRAM ёқилди → register; ўчирилди → remove. */
    @Test
    void pluginToggled_telegram_registersOrRemoves() {
        TelegramService service = mock(TelegramService.class);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(service);

        registrar.onPluginToggled(new PluginToggledEvent(PluginKey.TELEGRAM, true));
        verify(service).registerWebhookIfReady();

        registrar.onPluginToggled(new PluginToggledEvent(PluginKey.TELEGRAM, false));
        verify(service).removeWebhookRegistration();
    }

    /** Токен ўрнатилди (configured) → register; ўчирилди → ҳеч нарса (service ичида ўчган). */
    @Test
    void tokenChanged_configuredRegisters_unconfiguredNoop() {
        TelegramService service = mock(TelegramService.class);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(service);

        registrar.onTokenChanged(new TelegramTokenChangedEvent(true));
        verify(service).registerWebhookIfReady();

        registrar.onTokenChanged(new TelegramTokenChangedEvent(false));
        // configured=false да қўшимча амал йўқ (deleteToken webhook'ни ўзи ўчирган)
        Mockito.verifyNoMoreInteractions(service);
    }
}
