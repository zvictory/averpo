package com.averpo.erp.inventory.domain;

/**
 * Омбор ҳаракати турлари. Transfer иккита ёзув билан ифодаланади
 * (TRANSFER_OUT манбада + TRANSFER_IN манзилда) - ҳар ёзув айнан битта
 * омборга таъсир қилади, баланс ҳисоби ҳамма тур учун бир хил йўлдан
 * юради (spec, «Қатъий қарорлар»).
 *
 * @author Zafar
 */
public enum MovementType {

    /** Кирим - харид/receipt (6-босқичда Bill уланади). */
    IN,

    /** Чиқим - сотув/COGS (7-босқичда Invoice уланади). */
    OUT,

    /** Инвентаризация: ортиқча топилди (GL: INVENTORY Dt / shrinkage Cr). */
    ADJUST_IN,

    /** Инвентаризация: камомад (GL: shrinkage Dt / INVENTORY Cr). */
    ADJUST_OUT,

    /** Омборлараро кўчиришнинг кирим томони (GL проводка ЙЎҚ). */
    TRANSFER_IN,

    /** Омборлараро кўчиришнинг чиқим томони (GL проводка ЙЎҚ). */
    TRANSFER_OUT;

    /** Қолдиқни оширадими - баланс ҳисоби учун ягона белги. */
    public boolean inbound() {
        return this == IN || this == ADJUST_IN || this == TRANSFER_IN;
    }

    /** i18n сарлавҳа калити - ҳаракатлар жадвалида шу орқали кўрсатилади. */
    public String titleKey() { return "inv.type." + name(); }
}
