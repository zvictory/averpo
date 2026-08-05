@import com.averpo.erp.i18n.Msg
@param String key
@param Msg msg
<%-- Arbitr-079: майдон даражасидаги (?) тугмаси (help-system.md).
     key -> help.<key>.title/.body i18n жуфти; матн data-атрибутларда,
     диалогни main.jte даги глобал delegation листенер очади.
     type=button - label/форма ичида submit қилмайди; label ичида
     босилганда интерактив элемент сифатида click'ни ўзи ютади
     (label'га боғланган input toggle бўлмайди). --%>
<%-- helpbtn - JS delegation МАРКЕРИ (helpDialog.jte тингловчиси),
     кўриниши Penguin утилиталарда (Arbitr-122). Arbitr-134: size-4 +
     .65rem - лабел матни билан бир баландлик, кўзга халақит бермайди;
     чақириқ жойида лабел матни билан бирга inline-flex span'га ўралади. --%>
<button type="button" class="helpbtn inline-flex size-4 shrink-0 cursor-pointer items-center justify-center rounded-full border border-current/40 bg-transparent p-0 align-middle text-[.65rem] font-semibold leading-none text-current/60 transition hover:border-primary hover:bg-primary hover:text-on-primary print:hidden dark:hover:border-primary-dark dark:hover:bg-primary-dark dark:hover:text-on-primary-dark"
        data-help-title="${msg.lookup("help." + key + ".title")}"
        data-help-body="${msg.lookup("help." + key + ".body")}"
        aria-label="${msg.lookup("help.button")}">?</button>
