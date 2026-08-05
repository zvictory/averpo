@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param List<TaxRate> rates
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.taxRates", title = msg.lookup("tax.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("tax.title")}</h1>
        <div class="flex items-center gap-3">
            <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
        </div>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:720px;display:grid;gap:1rem;">
        <%-- Янги ставка --%>
        <form method="post" action="/settings/tax-rates"
              class="flex flex-wrap gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <input name="code" required placeholder="${msg.lookup("tax.col.code")}"
                   class="min-w-[90px] flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <input name="name" required placeholder="${msg.lookup("tax.col.name")}"
                   class="min-w-[130px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <input name="rate" required inputmode="decimal" placeholder="${msg.lookup("tax.col.rate")}"
                   class="min-w-[70px] flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tax.add")}</button>
        </form>

        @if(rates.isEmpty())
            <p class="text-current/60">${msg.localize("tax.empty")}</p>
        @else
                @template.shared.table(cls = "min-w-[560px]", c = @`
                    <thead>
                    <tr>
                        <th>${msg.localize("tax.col.code")}</th>
                        <th>${msg.localize("tax.col.name")}</th>
                        <th class="text-right">${msg.localize("tax.col.rate")}</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(TaxRate rate : rates)
                        <tr>
                            <td colspan="4">
                                <form method="post" action="/settings/tax-rates/${rate.getId().toString()}"
                                      style="display:flex;gap:.6rem;align-items:center;flex-wrap:wrap;">
                                    @template.shared.csrf(csrf = csrf)
                                    <input name="code" value="${rate.getCode()}" required
                                           class="min-w-[90px] flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                    <input name="name" value="${rate.getName()}" required
                                           class="min-w-[130px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                    <input name="rate" value="${rate.getRate().stripTrailingZeros().toPlainString()}" required
                                           inputmode="decimal"
                                           class="min-w-[70px] flex-1 text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                    <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 whitespace-nowrap">
                                        <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${rate.isActive()}">
                                        ${msg.localize("accounts.form.active")}
                                    </label>
                                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
                                </form>
                            </td>
                        </tr>
                    @endfor
                    </tbody>
            `)
        @endif
    </div>
`)
