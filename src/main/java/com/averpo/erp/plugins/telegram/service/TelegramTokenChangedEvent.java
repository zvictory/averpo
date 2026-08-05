package com.averpo.erp.plugins.telegram.service;

/**
 * Telegram bot токени ўзгарди (Arbitr-138) - {@code TelegramService}
 * saveToken/deleteToken якунида эълон қилинади. Webhook registrar (prod)
 * {@code @TransactionalEventListener(AFTER_COMMIT)} билан тинглаб
 * webhook'ни қайта рўйхатдан ўтказади ёки ўчиради.
 *
 * <p><b>Event'да СИР ЙЎҚ</b>: фақат {@code configured} ФАКТи узатилади -
 * токен ҳам, секрет ҳам event'да ташилмайди (PluginToggledEvent
 * нақши). Registrar керакли сирни ЎЗи базадан очади.
 *
 * <p>Дев/тест профилда тингловчи умуман йўқ (registrar
 * {@code @Profile("!dev & !test")}) - event жимгина ўтади.
 *
 * @param configured токен ўрнатилдими (true) ёки ўчирилдими (false)
 *
 * @author Zafar
 */
public record TelegramTokenChangedEvent(boolean configured) {
}
