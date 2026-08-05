@import com.averpo.erp.i18n.Msg
@param String action
@param Msg msg
@param String from = null
@param String to = null
@param String q = ""
@param gg.jte.Content extra = null

<%-- Умумий рўйхат филтр қатори (DEC-068, list-filters.md): оддий GET
     форма (bookmark/back ишлайди, audit-log нақши), мобилда wrap.
     from/to null берилса сана оралиғи чиқмайди (каталог рўйхатлари);
     extra - экранга хос select'лар (статус/контакт/тур/фаоллик) шу
     ерга қўйилади, номлари GET параметрга мос бўлиши шарт. Филтр
     ўзгарса page қайта юборилмайди - табиий page=0. «Тозалаш» -
     параметрсиз шу саҳифа. --%>
<form method="get" action="${action}"
      style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
    @if(from != null)
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.from")}
            <input type="date" name="from" value="${from}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
    @endif
    @if(to != null)
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.to")}
            <input type="date" name="to" value="${to}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
    @endif
    @if(extra != null)
        ${extra}
    @endif
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.text")}
        <input type="text" name="q" value="${q}" maxlength="100"
               class="mt-1 block max-w-56 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    </label>
    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("filter.apply")}</button>
    <a class="self-center text-primary underline dark:text-primary-dark" href="${action}">${msg.localize("filter.clear")}</a>
</form>
