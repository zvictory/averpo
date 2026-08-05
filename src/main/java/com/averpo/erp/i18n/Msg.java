package com.averpo.erp.i18n;

import gg.jte.support.LocalizationSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * JTE шаблонлар ва контроллерлар учун i18n кўприги.
 *
 * <p>JTE'нинг {@link LocalizationSupport}'ини Spring MessageSource'га
 * улайди: шаблонда {@code ${msg.localize("key")}} - param'li вариантда
 * JTE ўз substitution'ини ишлатади (MessageFormat апостроф тузоғи йўқ).
 * Жорий тил LocaleContextHolder'дан - CookieLocaleResolver белгилайди.
 *
 * @author Zafar
 */
@Component
@RequiredArgsConstructor
public class Msg implements LocalizationSupport {

    /** Spring message bundle'лари (messages*.properties). */
    private final MessageSource messageSource;

    /**
     * JTE localize() учун калит қиймати - жорий тилда.
     * Топилмаса калитнинг ўзи қайтади (бўш саҳифа эмас, кўриниб қолади).
     */
    @Override
    public String lookup(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    /**
     * Контроллер flash хабарлари учун параметрли вариант.
     * Диққат: args берилса MessageFormat ишлайди - калит матнида
     * апостроф ишлатманг ёки {@code ''} деб ёзинг.
     */
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    /**
     * ФОН оқимлари учун АНИҚ тилдаги қиймат (Arbitr-103 - Telegram
     * poller жавоблари).
     *
     * <p>Нега керак: {@link LocaleContextHolder} request'сиз оқимда
     * (poller thread'и, scheduler) сессия тилини БИЛМАЙДИ - у JVM'нинг
     * system locale'ига тушади, яъни хабар сервернинг {@code LANG}
     * созламасига қараб инглизча ёки русча чиқиб қоларди. Буни
     * Arbitr-103 тести тутди (бот ўзбекча ўрнига инглизча жавоб
     * берди). Фон хабарини юборувчи ҳар доим тилни ЎЗИ айтади.
     */
    public String getIn(java.util.Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }
}
