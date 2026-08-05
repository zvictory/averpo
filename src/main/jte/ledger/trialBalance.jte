@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.web.TrialBalanceController
@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@import java.time.LocalDate
@import java.util.List
@param List<TrialBalanceController.DisplayRow> rows
@param BigDecimal totalOpenDt
@param BigDecimal totalOpenCt
@param BigDecimal totalTurnDt
@param BigDecimal totalTurnCt
@param BigDecimal totalCloseDt
@param BigDecimal totalCloseCt
@param LocalDate from
@param LocalDate to
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.trialBalance", title = msg.lookup("tb.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-1.5 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("tb.heading")}</h1>
    <%-- U3: QBO услуби - катакларга код ёзилмайди, тепада битта ёзув --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>

    <form method="get" action="/reports/trial-balance"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.from")}
            <input type="date" name="from" value="${from.toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.to")}
            <input type="date" name="to" value="${to.toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tb.show")}</button>
    </form>

    @if(rows.isEmpty())
        <p class="text-current/60">${msg.localize("tb.empty")}</p>
    @else
            @template.shared.table(cls = "min-w-[900px]", c = @`
                <thead>
                <tr>
                    <th rowspan="2">${msg.localize("tb.col.account")}</th>
                    <th rowspan="2">${msg.localize("tb.col.type")}</th>
                    <th colspan="2" class="text-center">${msg.localize("tb.opening")}</th>
                    <th colspan="2" class="text-center">${msg.localize("tb.turnover")}</th>
                    <th colspan="2" class="text-center">${msg.localize("tb.closing")}</th>
                </tr>
                <tr>
                    <th class="text-right">${msg.localize("tb.dt")}</th>
                    <th class="text-right">${msg.localize("tb.ct")}</th>
                    <th class="text-right">${msg.localize("tb.dt")}</th>
                    <th class="text-right">${msg.localize("tb.ct")}</th>
                    <th class="text-right">${msg.localize("tb.dt")}</th>
                    <th class="text-right">${msg.localize("tb.ct")}</th>
                </tr>
                </thead>
                <tbody>
                @for(TrialBalanceController.DisplayRow row : rows)
                    <%-- T2: ҳисоботдан «Счёт амаллари»га drill-down - давр филтри ўтади --%>
                    <tr data-href="/accounts/${row.accountId().toString()}/transactions?from=${from.toString()}&to=${to.toString()}">
                        <td>
                            @if(row.code() != null)${row.code()} - @endif${row.name()}
                        </td>
                        <td>${msg.localize(row.typeKey())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.openDt())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.openCt())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.turnDt())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.turnCt())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.closeDt())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.closeCt())}</td>
                    </tr>
                @endfor
                </tbody>
                <tfoot>
                <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                    <td colspan="2">${msg.localize("tb.total")}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totalOpenDt)}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totalOpenCt)}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totalTurnDt)}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totalTurnCt)}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totalCloseDt)}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totalCloseCt)}</td>
                </tr>
                </tfoot>
        `)
        <p class="mt-2 text-[.85rem] text-current/60">${msg.localize("tb.note")}</p>
    @endif
`)
