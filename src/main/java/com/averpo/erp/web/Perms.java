package com.averpo.erp.web;

import com.averpo.erp.security.domain.Capability;
import com.averpo.erp.security.domain.Permission;
import com.averpo.erp.security.domain.RolePermissions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * Сайдбар/панель филтри учун жорий фойдаланувчининг соҳа рухсатлари
 * (Arbitr-092). main.jte (ва dashboard.jte тез амаллари) СТАТИК
 * {@link #current()} орқали олади - model attribute ЭМАС, чунки JTE'да
 * layout шаблонига фақат явный параметр етиб боради: perms'ни параметр
 * қилиш 102 саҳифа шаблонини ўзгартиришни талаб қиларди (карта тақиғи).
 * Render request thread'ида бўлгани учун SecurityContextHolder шу ерда
 * ишончли манба.
 *
 * <p>Бу фақат КЎРИНИШ филтри - server ҳақиқати SecurityConfig'даги
 * соҳа қоидаларида.
 */
public final class Perms {

    /** Жорий фойдаланувчининг authority номлари (тез contains учун). */
    private final Set<String> authorities;

    private Perms(Set<String> authorities) {
        this.authorities = authorities;
    }

    /**
     * Жорий сессия рухсатлари - auth йўқ бўлса (login саҳифаси, тизим
     * оқими) ҳамма нарса false бўлган бўш объект (NPE эмас).
     */
    public static Perms current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> names = new HashSet<>();
        if (auth != null) {
            for (GrantedAuthority granted : auth.getAuthorities()) {
                names.add(granted.getAuthority());
            }
        }
        return new Perms(names);
    }

    /** Соҳани кўра оладими - сайдбар гуруҳ/ҳавола кўринишини бошқаради. */
    public boolean view(Permission area) {
        return authorities.contains(RolePermissions.viewAuthority(area));
    }

    /** Соҳада ёза оладими - «+ Янги» бандлари ва тез амаллар филтри. */
    public boolean edit(Permission area) {
        return authorities.contains(RolePermissions.editAuthority(area));
    }

    /** Камида битта соҳада EDIT борми - «+ Янги» тугмасининг ўзи учун. */
    public boolean anyEdit() {
        for (String editAuthority : RolePermissions.allEditAuthorities()) {
            if (authorities.contains(editAuthority)) {
                return true;
            }
        }
        return false;
    }

    /** PERIOD_CLOSE имконияти борми - «Даврни ёпиш» сайдбар ҳаволаси учун. */
    public boolean periodClose() {
        return authorities.contains(Capability.PERIOD_CLOSE.name());
    }
}
