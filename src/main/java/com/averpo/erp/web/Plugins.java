package com.averpo.erp.web;

import com.averpo.erp.plugins.core.domain.PluginKey;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * JTE шаблонлар учун плагин гейти (Arbitr-113): меню бандлари ва созлама
 * бўлимлари плагин ёқиқлигини СТАТИК {@link #current()} орқали сўрайди -
 * {@link Perms}/{@link LayoutInfo} нақши (layout'га параметр қўшиш юзлаб
 * шаблонни ўзгартирарди). Ҳозир консумент йўқ - биринчи плагин менюси/
 * созламасини 103 (Telegram) шу гейт билан улайди.
 *
 * <p>Маълумотни {@code GlobalModelAttributes} ҳар request бошида
 * {@code PluginService.enabledKeys()} дан БИР марта ўқиб request
 * attribute'га қўяди - request ичидаги барча гейт саволлари шу
 * тўпламдан (қўшимча сўровсиз). Server ҳақиқати бу эмас: route/фича
 * гарови PluginService.isEnabled'нинг ўзида - бу фақат кўриниш филтри.
 *
 * @param enabled шу request'да ёқиқ плагинлар тўплами
 */
public record Plugins(Set<PluginKey> enabled) {

    /** Request attribute калити. */
    public static final String ATTR = Plugins.class.getName();

    /**
     * Жорий request гейти - GlobalModelAttributes қўйган; йўқ бўлса
     * (login саҳифаси, тизим оқими) ҳамма плагин ўчиқ кўринади (NPE эмас).
     */
    public static Plugins current() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes sra
                && sra.getRequest().getAttribute(ATTR) instanceof Plugins plugins) {
            return plugins;
        }
        return new Plugins(Set.of());
    }

    /** Плагин ёқиқми - меню/созлама бандининг кўриниш шарти. */
    public boolean on(PluginKey key) {
        return enabled.contains(key);
    }
}
