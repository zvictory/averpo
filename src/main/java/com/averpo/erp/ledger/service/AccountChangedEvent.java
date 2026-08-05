package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.Account;

/**
 * Счёт яратилди/таҳрирланди/нофаол қилинди - {@code AccountService}
 * create/update нуқталаридан эълон қилинади (DEC-062, ACCOUNT_*).
 * CSV импорт йўли (importCsv) АТАЙЛАБ event бермайди - default chart'даги
 * ~40 счёт журнални тошириб юборарди, у {@link ChartImportedEvent} билан
 * жамланган ҳолда қамралади.
 *
 * <p>Event ledger ичида туради, тингловчи audit модулида -
 * {@link JournalEntryPostedEvent} нақши (қоида №6: ledger audit'ни билмайди).
 *
 * @param account таъсирланган счёт (ном/detail type тафсилот учун)
 * @param action  нима бўлди - audit тури шунга қараб танланади
 * @param changes таҳрирда ўзгарган майдонлар «эски → янги» рўйхати
 *                (created'да null; ўзгаришсиз update event бермайди)
 */
public record AccountChangedEvent(Account account, Action action, String changes) {

    /** Ҳодиса тури - audit модули AuditEventType'га ўгиради. */
    public enum Action {
        /** Янги счёт яратилди (форма ёки Excel import йўли). */
        CREATED,
        /** Счёт таҳрирланди (реактивация ҳам шу ерда - active диффда). */
        UPDATED,
        /** Счёт нофаол қилинди (active true → false). */
        DEACTIVATED
    }
}
