@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.service.PayrollRegisterService
@import com.averpo.erp.shared.web.Fmt
@param PayrollRegisterService.Register register
@param String homeCurrency
@param String companyName = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String error = null

@template.layout.main(helpKey = "page.payrollRegister", title = msg.lookup("preg.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <%-- Print полиши (Nargiza-047, statement.jte нақши): жадвал
         min-width:920px A4 (≈688px)га сиғмайди - чоп этишда 0 +
         компакт padding (қатор чекланмайди). Локал <style> ўрнига
         print: утилиталар фрагмент cls/wrap'ида (Arbitr-120). --%>
    <%-- Сарлавҳа/форма/изоҳ - чоп этишда яширин (print:hidden) --%>
    <div class="print:hidden">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("preg.title")}</h1>
            @if(!register.rows().isEmpty())
                <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" onclick="window.print()">${msg.localize("statement.print")}</button>
            @endif
        </div>

        @template.shared.alert(error = error)

        <form method="get" action="/reports/payroll-register" class="mb-4 flex flex-wrap items-end gap-3">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("preg.period")}
                <input type="month" name="period" value="${register.period()}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("preg.apply")}</button>
        </form>

        <%-- Барча суммалар home (UZS) - clearing қолдиқ owed = ходимга қарз --%>
        <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}. ${msg.localize("preg.note")} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>
    </div>

    @if(register.rows().isEmpty())
        <p class="text-current/60">${msg.localize("preg.empty")}</p>
    @else
        <%-- Print сарлавҳа: фақат чоп этишда (statement.jte нақши) -
             компания + ҳисобот номи + давр + валюта контексти --%>
        <div class="mb-4 hidden print:block">
            <h2 class="mb-1 text-xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${companyName}</h2>
            <div>${msg.localize("preg.title")}: <b>${register.period()}</b></div>
            <div class="text-sm text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}</div>
        </div>

        @template.shared.table(cls = "min-w-[920px] print:min-w-0 print:[&_th]:p-[2px_5px]! print:[&_td]:p-[2px_5px]! print:[&_th]:text-[11px] print:[&_td]:text-[11px]", wrap = "print:overflow-visible print:rounded-none print:border-0", c = @`
            <thead>
            <tr>
                <th>${msg.localize("prun.row.employee")}</th>
                <th class="text-right">${msg.localize("preg.col.opening")}</th>
                <th class="text-right">${msg.localize("prun.col.gross")}</th>
                <th class="text-right">${msg.localize("prun.col.incomeTax")}</th>
                <th class="text-right">${msg.localize("prun.col.pension")}</th>
                <th class="text-right">${msg.localize("prun.col.net")}</th>
                <th class="text-right">${msg.localize("preg.col.paid")}</th>
                <th class="text-right">${msg.localize("preg.col.closing")}</th>
            </tr>
            </thead>
            <tbody>
            @for(PayrollRegisterService.Row row : register.rows())
                <tr>
                    <td>${row.employeeName()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.openingOwed())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.gross())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.incomeTax())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.pension())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.net())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.paid())}</td>
                    <td class="text-right font-semibold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.closingOwed())}</td>
                </tr>
            @endfor
            </tbody>
            <tfoot>
            <tr>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("aging.col.total")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().openingOwed())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().gross())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().incomeTax())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().pension())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().net())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().paid())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.totals().closingOwed())}</td>
            </tr>
            </tfoot>
        `)
    @endif
`)
