package com.averpo.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Locale;

/**
 * UI тили конфигурацияси: уз(кирилл, default) / ру / en.
 *
 * <p>Танлов cookie'да сақланади, {@code ?lang=ru} query параметри билан
 * алмашади. Фойдаланувчи тизими пайдо бўлганда (кейинги босқич) тил
 * user profile'га кўчади - QBO услуби.
 *
 * @author Zafar
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Cookie'га асосланган locale: default ўзбекча (кирилл). */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("AVERPO_LANG");
        resolver.setDefaultLocale(Locale.of("uz"));
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    /** ?lang=uz|ru|en параметри билан тил алмаштириш. */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    /** Interceptor'ни рўйхатдан ўтказади - ҳар сўровда ишлайди. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
