@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.pricing.domain.PriceList
@import com.averpo.erp.shared.domain.Currency
@import java.util.List
@param List<PriceList> lists
@param List<Currency> currencies
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.priceLists", title = msg.lookup("plist.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("plist.title")}</h1>
        <div class="flex items-center gap-3">
            <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
        </div>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:720px;display:grid;gap:1rem;">
        <p class="text-[.9rem] text-current/60">${msg.localize("plist.hint")}</p>

        <%-- Янги рўйхат: ном + валюта (қолгани картада) --%>
        <form method="post" action="/settings/price-lists"
              class="flex flex-wrap gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <input name="name" required placeholder="${msg.lookup("plist.name")}"
                   class="min-w-[160px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <%-- Валюта - native select (сурув қарори: 3-10 ёзув) --%>
            <select name="currency" class="min-w-[90px] flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                @for(Currency currency : currencies)
                    <option value="${currency.getCode()}">${currency.getCode()}</option>
                @endfor
            </select>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("plist.add")}</button>
        </form>

        @if(lists.isEmpty())
            <p class="text-current/60">${msg.localize("plist.empty")}</p>
        @else
                @template.shared.table(cls = "min-w-[560px]", c = @`
                    <thead>
                    <tr>
                        <th>${msg.localize("plist.name")}</th>
                        <th>${msg.localize("plist.currency")}</th>
                        <th>${msg.localize("plist.period")}</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(PriceList list : lists)
                        <tr data-href="/settings/price-lists/${list.getId().toString()}">
                            <td>
                                ${list.getName()}
                                @if(list.isDefaultList())
                                    <span class="ml-1 rounded bg-success/15 px-1.5 py-0.5 text-xs text-success">${msg.localize("plist.default")}</span>
                                @endif
                                @if(!list.isActive())
                                    <span class="ml-1 text-[.8rem] text-current/50">(${msg.localize("accounts.inactive")})</span>
                                @endif
                            </td>
                            <td>${list.getCurrency().getCode()}</td>
                            <td>
                                @if(list.getValidFrom() != null || list.getValidTo() != null)
                                    ${list.getValidFrom() == null ? "..." : list.getValidFrom().toString()} -
                                    ${list.getValidTo() == null ? "..." : list.getValidTo().toString()}
                                @endif
                            </td>
                            <td class="text-right text-sm text-primary dark:text-primary-dark">${msg.localize("common.view")}</td>
                        </tr>
                    @endfor
                    </tbody>
            `)
        @endif
    </div>
`)
