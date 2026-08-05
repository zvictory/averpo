@import com.averpo.erp.i18n.Msg
@param java.time.LocalDate closingDate = null
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

<%-- Давр ёпилиш санасининг алоҳида саҳифаси (user-roles.md PERIOD_CLOSE):
     CHIEF_ACCOUNTANT /settings'га кира олмайди, даврни эса у бошқаради.
     Сақлаш тугмаси canEdit'сиз - бу саҳифага фақат PERIOD_CLOSE эгалари
     етади (SecurityConfig), UI'да қайта филтрлаш шарт эмас. --%>
@template.layout.main(helpKey = "page.closingDate", title = msg.lookup("closing.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-4 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("closing.title")}</h1>

    @template.shared.alert(message = message, error = error)

    <form method="post" action="/settings/closing-date"
          class="grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("settings.closingDate")}
            <input type="date" name="closingDate"
                   value="${closingDate == null ? "" : closingDate.toString()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <span class="mt-1 block text-[.85rem] text-current/60">${msg.localize("settings.closingDateHint")}</span>
        </label>

        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
    </form>
`)
