package com.averpo.erp.plugins.telegram.service;

import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginToggledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Prod'да Telegram webhook'ни рўйхатдан ўтказиб турадиган компонент
 * (Arbitr-138) - «қачон» бошқарувчиси, ишлов мантиғи
 * {@link TelegramService}да. Учта нуқтада webhook ҳолати
 * янгиланади: старт, плагин toggle, токен ўзгариши.
 *
 * <p><b>Нега {@code @Profile("!dev & !test")}</b>: webhook фақат
 * профилсиз prod'да - дев polling ишлатади (Telegram localhost'га ета
 * олмайди), тест эса тармоққа умуман чиқмайди. Демак дев/тестда бу
 * bean УМУМАН яратилмайди.
 *
 * <p><b>Нега {@code ApplicationRunner} (SmartLifecycle эмас)</b>:
 * {@code stop()}да webhook ЎЧИРИЛМАЙДИ - рестарт оралиғида Telegram
 * хабарларни сақлаб, янги instance кўтарилгач етказади. Webhook'ни
 * атайлаб фақат плагин ўчганда/токен олинганда бекор қиламиз.
 *
 * <p><b>Нега {@code AFTER_COMMIT}</b>: toggle/токен ўзгариши commit
 * бўлмаса webhook ҳам ўзгармаслиги керак (изчиллик) - rollback'да
 * тингловчи умуман чақирилмайди.
 *
 * @author Zafar
 */
@Component
@Profile("!dev & !test")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookRegistrar implements ApplicationRunner {

    /** Webhook ҳолати мантиғи (registerWebhookIfReady/remove). */
    private final TelegramService telegramService;

    /**
     * Старт'да webhook'ни рўйхатдан ўтказади (плагин ёқиқ + бот
     * созланган + AVERPO_PUBLIC_URL бор бўлса). Шарт етишмаса илова
     * ЙИҚИЛМАЙДИ - лог факти ёзилиб, кейинги toggle/токен event'ида
     * уланади (fail-safe).
     */
    @Override
    public void run(ApplicationArguments args) {
        telegramService.registerWebhookIfReady();
    }

    /**
     * Плагин toggle: ёқилса webhook'ни рўйхатдан ўтказади, ўчса бекор
     * қилади. Фақат TELEGRAM калити (бошқа плагинлар webhook'га
     * тегишсиз).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPluginToggled(PluginToggledEvent event) {
        if (event.key() != PluginKey.TELEGRAM) {
            return;
        }
        if (event.enabled()) {
            telegramService.registerWebhookIfReady();
        } else {
            telegramService.removeWebhookRegistration();
        }
    }

    /**
     * Токен ўзгариши: ўрнатилса webhook'ни янги токен билан қайта
     * рўйхатдан ўтказади. Ўчирилиш ҳолати (configured=false)
     * TelegramService.deleteToken ичида webhook'ни аллақачон бекор
     * қилган - бу ерда қўшимча амал йўқ (event факти лог сифатида
     * кифоя).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTokenChanged(TelegramTokenChangedEvent event) {
        if (event.configured()) {
            telegramService.registerWebhookIfReady();
        }
    }
}
