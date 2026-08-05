@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.shared.domain.ExchangeRate
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param Currency currency
@param String homeCurrency
@param List<ExchangeRate> rates
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = msg.get("currency.rates.title", currency.getCode()), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("currency.rates.title", currency.getCode())}</h1>
        <a href="/settings/currencies" class="text-primary underline dark:text-primary-dark">${msg.localize("currency.backToList")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:520px;display:grid;gap:1rem;">
        <form method="post" action="/settings/currencies/${currency.getCode()}/rates"
              class="grid gap-2.5 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:.6rem;align-items:end;">
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("currency.rates.date")}
                    <input type="date" name="date" value="${today}" required
                           class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("currency.rates.rate", currency.getCode(), homeCurrency)}
                    <input name="rate" class="money mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" required
                          >
                </label>
            </div>
            <div style="display:flex;gap:.6rem;align-items:center;">
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
                <span class="text-sm text-current/60">${msg.localize("currency.rates.hint")}</span>
            </div>
        </form>

        @if(rates.isEmpty())
            <p class="text-current/60">${msg.localize("currency.rates.empty")}</p>
        @else
                @template.shared.table(c = @`
                    <thead>
                    <tr>
                        <th>${msg.localize("currency.rates.date")}</th>
                        <th class="text-right">${msg.localize("currency.col.latestRate")}</th>
                        <th>${msg.localize("currency.rates.source")}</th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(var i = 0; i < rates.size(); i++)
                        !{var rate = rates.get(i);}
                        <tr>
                            <td>
                                ${rate.getRateDate().toString()}
                                <%-- X-016: тарих янгидан эскига - биринчи қатор
                                     АМАЛДАГИ курс (бир санага кўп ёзув бўлади) --%>
                                @if(i == 0)
                                    <span class="ml-1 rounded bg-success/15 px-1.5 py-0.5 text-xs text-success">${msg.localize("rate.current")}</span>
                                @endif
                            </td>
                            <%-- DEC-065: тарих қатори ҳам йўналтирилган кўринишда -
                                 сақланадиган қиймат каталог конвенциясида қолади --%>
                            !{var rateOr = Fmt.orient(currency.getCode(), homeCurrency, rate.getRate());}
                            <td class="text-right whitespace-nowrap">${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())}</td>
                            <td>${msg.localize("rate.source." + rate.getSource().name())}</td>
                        </tr>
                    @endfor
                    </tbody>
            `)
        @endif
    </div>
`)
