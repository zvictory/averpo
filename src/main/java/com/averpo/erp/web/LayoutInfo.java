package com.averpo.erp.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Layout контексти (Arbitr-101/112 рефайнмент): main.jte топбар бренди,
 * sidebar тепа 3 поғонали бренди (логотип → онбординг тугагач company
 * name → AVERPO fallback), mustChange banner ва sidebar аватар/ном учун.
 *
 * <p>{@link Perms} нақши - JTE layout шаблони СТАТИК {@link #current()}
 * орқали олади (model attribute ЭМАС: ҳар JTE'ни ўзгартирмаслик, зона
 * тақиғи; JTE layout'га фақат явный параметр етади). Маълумотни
 * {@code GlobalModelAttributes} ҳар request'да request attribute'га
 * қўяди (у CompanySettingsService/UserService bean'ларини inject қила
 * олади; static helper эса RequestContextHolder орқали ўқийди - bean
 * керак эмас, Perms.current() SecurityContextHolder'дан олгани каби).
 *
 * @author Zafar
 */
public record LayoutInfo(String companyName, UUID brandLogoId, boolean setupDone,
                         boolean mustChangePassword, String displayName, String username,
                         UUID avatarImageId) {

    /**
     * Бренд fallback номи - логотип созланмаган ва онбординг ҳали
     * тугамаган (company name реал эмас) ҳолда sidebar тепаси ва login
     * шу вендор брендини кўрсатади.
     */
    public static final String FALLBACK_BRAND = "AVERPO";

    /** Request attribute калити. */
    public static final String ATTR = LayoutInfo.class.getName();

    /** Fallback (auth йўқ ёки request контекстисиз - login саҳифаси). */
    public static LayoutInfo fallback() {
        return new LayoutInfo(FALLBACK_BRAND, null, false, false, null, null, null);
    }

    /**
     * Жорий request layout контексти - GlobalModelAttributes қўйган;
     * йўқ бўлса fallback (NPE эмас).
     */
    public static LayoutInfo current() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes sra
                && sra.getRequest().getAttribute(ATTR) instanceof LayoutInfo layout) {
            return layout;
        }
        return fallback();
    }

    /**
     * Компания бренд логоси созланганми (топбар + sidebar тепа). Логотип
     * йўқ бўлса sidebar {@link #showCompanyName()} бўйича company name ёки
     * AVERPO fallback кўрсатади (банд 112.4 white-label).
     */
    public boolean hasBrandLogo() {
        return brandLogoId != null;
    }

    /**
     * Sidebar тепаси логотип ўрнига company name кўрсатсинми - онбординг
     * тугаган ({@code setupDone}) ва ном реал (бўш эмас) бўлса. Тугамаган
     * бўлса default «Компания» placeholder эмас, AVERPO fallback чиқади
     * (фойдаланувчи 2026-07-17: боши AVERPO, setup'дан кейин ўз номи).
     */
    public boolean showCompanyName() {
        return setupDone && companyName != null && !companyName.isBlank();
    }

    /** Аватар борми (sidebar - расм ёки placeholder бош ҳарфли доира). */
    public boolean hasAvatar() {
        return avatarImageId != null;
    }

    /** Sidebar/топбар placeholder бош ҳарфи (displayName биринчи ҳарфи). */
    public String initial() {
        return displayName == null || displayName.isBlank()
                ? "?" : displayName.substring(0, 1).toUpperCase();
    }
}
