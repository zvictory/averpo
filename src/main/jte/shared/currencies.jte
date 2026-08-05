@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.CurrencyController.CurrencyRow
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<CurrencyRow> rows
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = msg.lookup("currency.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("currency.title")}</h1>
        <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:760px;display:grid;gap:1rem;">
        <%-- ЦБ импорти: scheduler кунига икки марта тортади, бу тугма
             кутмасдан/тарихий сана учун --%>
        <form method="post" action="/settings/currencies/import"
              class="flex flex-wrap items-center gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <input type="date" name="date" value="${today}" class="rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("currency.import")}</button>
            <span class="text-sm text-current/60">${msg.localize("currency.importHint")}</span>
        </form>

            @template.shared.table(c = @`
                <thead>
                <tr>
                    <th>${msg.localize("currency.col.code")}</th>
                    <th>${msg.localize("currency.col.name")}</th>
                    <th>${msg.localize("currency.col.symbol")}</th>
                    <th class="text-right">${msg.localize("currency.col.latestRate")}</th>
                    <th>${msg.localize("currency.col.active")}</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                @for(CurrencyRow row : rows)
                    <%-- T8: қатор босилса курс тарихи; home'нинг курс саҳифаси йўқ --%>
                    <tr data-href="${row.home() ? null : "/settings/currencies/" + row.currency().getCode() + "/rates"}">
                        <td>
                            <b>${row.currency().getCode()}</b>
                            @if(row.home())
                                <span class="rounded bg-info/15 px-1.5 py-0.5 text-xs text-info">home</span>
                            @endif
                        </td>
                        <td>${row.currency().getName()}</td>
                        <td>${row.currency().getSymbol()}</td>
                        <td class="text-right whitespace-nowrap">
                            @if(row.home())
                                1
                            @elseif(row.latest() != null)
                                <%-- DEC-065: рўйхатда ҳам курс кучли валюта базисида --%>
                                !{var rateOr = Fmt.orient(row.currency().getCode(), homeCurrency, row.latest().getRate());}
                                ${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())}
                                <span class="text-[.8rem] text-current/50">(${row.latest().getRateDate().toString()})</span>
                            @else
                                <span class="text-current/50">${msg.localize("currency.noRate")}</span>
                            @endif
                        </td>
                        <td>${row.currency().isActive() ? "✓" : ""}</td>
                        <td class="text-right whitespace-nowrap">
                            @if(!row.home())
                                <a href="/settings/currencies/${row.currency().getCode()}/rates"
                                   class="mr-2.5 text-primary underline dark:text-primary-dark">${msg.localize("currency.rates")}</a>
                                <form method="post" action="/settings/currencies/${row.currency().getCode()}/active"
                                      style="display:inline;">
                                    @template.shared.csrf(csrf = csrf)
                                    <input type="hidden" name="active" value="${row.currency().isActive() ? "false" : "true"}">
                                    <%-- Ҳолатга қараб вариант: фаол → danger (деактивация),
                                         нофаол → primary (активлаштириш); кичик ўлчам (қатор ичи) --%>
                                    <button type="submit"
                                            class="${row.currency().isActive()
                                                ? "cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75"
                                                : "cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark"}">
                                        ${row.currency().isActive() ? msg.localize("currency.deactivate") : msg.localize("currency.activate")}
                                    </button>
                                </form>
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
        `)
    </div>
`)
