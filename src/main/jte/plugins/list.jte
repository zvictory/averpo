@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.plugins.core.web.PluginController.PluginRow
@import java.util.List
@param List<PluginRow> rows
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.plugins", title = msg.lookup("plugin.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("plugin.title")}</h1>
        <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Реестр тушунчаси бир жумлада (спец: плагин = built-in ихтиёрий
         фича, ёқилса менюси/созламаси пайдо бўлади) --%>
    <p class="mb-4 max-w-[640px] text-sm text-current/60">${msg.localize("plugin.intro")}</p>

    <div class="max-w-[760px]">
        @template.shared.table(cls = "min-w-[520px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("plugin.col.plugin")}</th>
                <th>${msg.localize("plugin.col.state")}</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            @for(PluginRow row : rows)
                <tr>
                    <td>
                        <b class="text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize(row.key().nameKey())}</b>
                        <p class="mt-0.5 text-sm text-current/60">${msg.localize(row.key().descKey())}</p>
                    </td>
                    <td>
                        @template.shared.badge(value = row.enabled() ? "ENABLED" : "DISABLED", msg = msg, family = "plugin.state")
                    </td>
                    <td class="text-right whitespace-nowrap">
                        <%-- «Созлаш →» фақат ёқиқ ва созлама route'и мавжуд
                             плагинда (TELEGRAM route'ини 103 тўлдиради) --%>
                        @if(row.enabled() && row.key().getSettingsRoute() != null)
                            <a href="${row.key().getSettingsRoute()}"
                               class="mr-2.5 text-primary underline dark:text-primary-dark">${msg.localize("plugin.configure")} →</a>
                        @endif
                        <form method="post" action="/settings/plugins/${row.key().name()}/toggle"
                              style="display:inline;">
                            @template.shared.csrf(csrf = csrf)
                            <input type="hidden" name="enabled" value="${row.enabled() ? "false" : "true"}">
                            <%-- Ҳолатга қараб вариант (currencies нақши): ёқиқ →
                                 danger (ўчириш), ўчиқ → primary (ёқиш); кичик ўлчам --%>
                            <button type="submit"
                                    class="${row.enabled()
                                        ? "cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75"
                                        : "cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark"}">
                                ${row.enabled() ? msg.localize("plugin.disable") : msg.localize("plugin.enable")}
                            </button>
                        </form>
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    </div>
`)
