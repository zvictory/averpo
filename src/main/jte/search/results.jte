@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.search.service.SearchResults
@param SearchResults results
@param String q = ""
@param Msg msg

<%-- Глобал қидирув натижаси (HTMX dropdown partial ва /search саҳифаси
     танасида бир хил). Гуруҳлар тартиби QBO Navigate услубида: ҳужжат →
     контакт → товар → счёт → экран. --%>
@if(results.isEmpty())
    <div class="px-3 py-3.5">
        <p class="text-sm text-current/60">${msg.localize("search.noResults")}</p>
        <p class="mt-1 text-[.82rem] text-current/50">${msg.localize("search.hint")}</p>
    </div>
@else
    @template.search.group(msg = msg, title = msg.lookup("search.group.documents"), hits = results.documents())
    @template.search.group(msg = msg, title = msg.lookup("search.group.contacts"), hits = results.contacts())
    @template.search.group(msg = msg, title = msg.lookup("search.group.items"), hits = results.items())
    @template.search.group(msg = msg, title = msg.lookup("search.group.accounts"), hits = results.accounts())
    @template.search.group(msg = msg, title = msg.lookup("search.group.screens"), hits = results.screens())
@endif
