@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.search.service.SearchResults
@param SearchResults results
@param String q = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

<%-- JS'сиз fallback саҳифа (spec «JS'сиз fallback»): sidebar қидирув
     триггери (оддий <a href="/search">) ёки палитра инпутида танловсиз
     Enter шу манзилга GET юборади - натижалар оддий саҳифада чиқади.
     HTMX/Alpine керак эмас - форма ва линклар JS'сиз ишлайди. --%>
@template.layout.main(helpKey = "page.search", title = msg.lookup("search.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("search.title")}</h1>
    <form method="get" action="/search" role="search" class="mb-5 mt-4 flex max-w-[560px] gap-2">
        <input type="search" name="q" value="${q}" autofocus autocomplete="off"
               placeholder="${msg.lookup("search.placeholder")}"
               class="flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("search.button")}</button>
    </form>
    @if(q != null && !q.isBlank())
        <div class="max-w-[560px] rounded-radius border border-outline bg-surface p-1 dark:border-outline-dark dark:bg-surface-dark">
            @template.search.results(results = results, q = q, msg = msg)
        </div>
    @endif
`)
