@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.service.ProfitAndLossByClassService.Report
@import com.averpo.erp.ledger.service.ProfitAndLossByClassService.Row
@import com.averpo.erp.ledger.service.ProfitAndLossByClassService.Section
@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@import java.util.List
@param Report report
@param List<String> columns
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.plByClass", title = msg.lookup("plc.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-1 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("plc.title")}</h1>
    <%-- Пул қоидаси: ҳисобот сарлавҳасида «Барча суммалар home да» --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>

    <form method="get" action="/reports/profit-and-loss-by-class"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("entries.filter.from")}
            <input type="date" name="from" value="${report.from().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("entries.filter.to")}
            <input type="date" name="to" value="${report.to().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("entries.filter.apply")}</button>
    </form>

    <%-- Устун кўп бўлиши мумкин - эни динамик, шунинг учун фрагментнинг
         tableStyle параметри (Tailwind runtime қийматни кўрмайди) --%>
    @template.shared.table(tableStyle = "min-width:" + (480 + columns.size() * 130) + "px;", c = @`
        <thead>
        <tr>
            <th class="min-w-[220px]"></th>
            @for(String column : columns)
                <th class="whitespace-nowrap text-right">${column}</th>
            @endfor
            <th class="whitespace-nowrap text-right">${msg.localize("plc.total")}</th>
        </tr>
        </thead>
        <tbody>
        @template.reports.plcSection(section = report.income(), title = msg.lookup("pl.income"), totalTitle = msg.lookup("pl.totalIncome"))
        @template.reports.plcSection(section = report.cogs(), title = msg.lookup("pl.cogs"), totalTitle = msg.lookup("pl.totalCogs"))
        @template.reports.plcComputedRow(cells = report.grossProfit(), total = report.grossProfitTotal(), title = msg.lookup("pl.grossProfit"))
        @template.reports.plcSection(section = report.expenses(), title = msg.lookup("pl.expenses"), totalTitle = msg.lookup("pl.totalExpenses"))
        @template.reports.plcComputedRow(cells = report.operatingIncome(), total = report.operatingIncomeTotal(), title = msg.lookup("pl.operatingIncome"))
        @template.reports.plcSection(section = report.otherIncome(), title = msg.lookup("pl.otherIncome"), totalTitle = msg.lookup("pl.totalOtherIncome"))
        @template.reports.plcSection(section = report.otherExpenses(), title = msg.lookup("pl.otherExpenses"), totalTitle = msg.lookup("pl.totalOtherExpenses"))
        @template.reports.plcComputedRow(cells = report.netOtherIncome(), total = report.netOtherIncomeTotal(), title = msg.lookup("pl.netOtherIncome"))
        @template.reports.plcComputedRow(cells = report.netIncome(), total = report.netIncomeTotal(), title = msg.lookup("pl.netIncome"), emphasize = true)
        </tbody>
    `)
`)
