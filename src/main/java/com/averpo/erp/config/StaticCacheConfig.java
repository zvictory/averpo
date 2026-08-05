package com.averpo.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Статик ресурсларга табақалашган Cache-Control (Arbitr-131).
 *
 * <p>Нега керак: Spring Security default'и ҲАМма жавобга (статикларга
 * ҳам) {@code Cache-Control: no-store} қўяди - шрифт ҳеч қачон
 * кэшланмай ҳар reload'да қайта тортилади (FOUT: матн аввал заҳира
 * шрифтда чизилиб кейин Inter'га «сакрайди»), Cloudflare ҳам origin
 * no-store'ига бўйсуниб статикларни кэшламайди. Бу ерда ResourceHandler
 * ўз Cache-Control'ини қўйгани учун Security'нинг
 * CacheControlHeadersWriter'и ўзи четланади (header мавжуд бўлса
 * ёзмайди - ҳужжатлаштирилган хулқ).
 *
 * <p>Барча статик гуруҳ 30 кун + immutable: шрифт/вендор файллари
 * фақат версия кўтарилганда ўзгаради, ўз css/js'имиз эса deploy'да
 * ўзгарса ҳам браузерга ЯНГИ URL бўлиб келади - шаблонлардаги
 * {@code ?v=<build вақти>} (Arbitr-137, {@link com.averpo.erp.web.Assets})
 * ҳар build'да алмашади, эски кэш ёзуви эса ўқилмай ўз-ўзидан
 * эскиради. Шунга revalidation ҳам керак эмас (immutable - reload'да
 * ҳам сўралмайди). HTML/динамик саҳифаларга бу handler'лар тегмайди -
 * Security no-store ҚОЛАДИ (молия маълумоти браузер кэшига тушмайди),
 * бу StaticCacheWebTest'да қотирилган.
 *
 * @author Zafar
 */
@Configuration
public class StaticCacheConfig implements WebMvcConfigurer {

    /**
     * Статик ресурслар: 30 кун + immutable (reload'да ҳам
     * revalidation сўралмайди - шрифт сакрашининг илдизи шу эди).
     */
    private static final CacheControl LONG_LIVED =
            CacheControl.maxAge(Duration.ofDays(30)).immutable();

    /**
     * Гуруҳларга мос handler'лар. {@code /js/vendor/**} {@code /js/**}
     * ичида бўлса ҳам АЛОҲИДА рўйхатдан ўтади: вендор файллари узун
     * кэшни ўз ҳақи билан олади - {@code /js/**} сиёсати кейин
     * ўзгарса ҳам (масалан ?v= олиб ташланса) улар шу ерда қолади;
     * PathPattern аниқроғини танлайди. Рўйхатга кирмаган статиклар
     * (масалан import-template.xlsx) Boot'нинг default {@code /**}
     * handler'ида қолади - уларга Security no-store'и аввалгидек
     * ишлайди.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(LONG_LIVED);
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCacheControl(LONG_LIVED);
        registry.addResourceHandler("/js/vendor/**")
                .addResourceLocations("classpath:/static/js/vendor/")
                .setCacheControl(LONG_LIVED);
        // Favicon ҳам узун гуруҳда: ҳар саҳифада сўралади, амалда
        // ўзгармайди (ўзгарса - файл номи версияланади)
        registry.addResourceHandler("/favicon.svg")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(LONG_LIVED);
        // Ўз css/js'имиз ҳам узун кэшда: янги build янги ?v= беради
        // (Arbitr-137) - эски ёзув кэшда ётаверади, ҳеч ким сўрамайди
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(LONG_LIVED);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(LONG_LIVED);
        // Расм активлари (Arbitr-143 вендор логоси) - css/js каби узун
        // кэш: линкда ?v=<build вақти> (Assets) янги build'да алмашади
        registry.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/")
                .setCacheControl(LONG_LIVED);
    }
}
