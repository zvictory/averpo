@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.domain.PayrollRun
@import com.averpo.erp.payroll.domain.PayrollRunLine
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param PayrollRun run
@param Map<UUID, String> employeeNames
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = run.getRunNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "payrollRun") ${run.getRunNumber()}</h1>
        <a href="/payroll" class="text-primary underline dark:text-primary-dark">${msg.localize("prun.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("prun.col.period")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${run.getPeriod()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("prun.col.runDate")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${run.getRunDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = run.getStatus().name(), msg = msg)</span>
        </div>
        @if(run.getEntryId() != null)
            <%-- JE ҳаваласи (spec) - post'да сақланган entry id --%>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("prun.view.entry")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark"><a href="/journal-entries/${run.getEntryId().toString()}" class="text-primary underline dark:text-primary-dark">${msg.localize("prun.view.entryLink")}</a></span>
            </div>
        @endif
    </div>

    @if(run.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${run.getMemo()}</p>
    @endif

    <p class="mb-2 text-[.9rem] text-current/60">${msg.localize("prun.view.amountsIn", homeCurrency)}</p>
    @template.shared.table(cls = "min-w-[760px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("prun.row.employee")}</th>
            <th class="text-right">${msg.localize("prun.col.gross")}</th>
            <th class="text-right">${msg.localize("prun.col.incomeTax")}</th>
            <th class="text-right">${msg.localize("prun.col.pension")}</th>
            <th class="text-right">${msg.localize("prun.col.socialTax")}</th>
            <th class="text-right">${msg.localize("prun.col.net")}</th>
        </tr>
        </thead>
        <tbody>
        !{java.math.BigDecimal tI = java.math.BigDecimal.ZERO;}
        !{java.math.BigDecimal tP = java.math.BigDecimal.ZERO;}
        !{java.math.BigDecimal tS = java.math.BigDecimal.ZERO;}
        @for(PayrollRunLine line : run.getLines())
            !{tI = tI.add(line.getIncomeTax());}
            !{tP = tP.add(line.getPension());}
            !{tS = tS.add(line.getSocialTax());}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${employeeNames.getOrDefault(line.getEmployeeId(), "-")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getGross())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getIncomeTax())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getPension())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getSocialTax())}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(line.getNet())}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
            <td colspan="2" class="text-right">${msg.localize("prun.form.total")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(run.totalGross())}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(tI)}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(tP)}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(tS)}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(run.totalNet())}</td>
        </tr>
        </tfoot>
    `)

    @if(canEdit != null && canEdit && run.getStatus() == PayrollRun.Status.DRAFT)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
            <form method="post" action="/payroll/${run.getId().toString()}/post">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("prun.view.post")}</button>
            </form>
            <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" href="/payroll/${run.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
        </div>
    @elseif(canEdit != null && canEdit && run.getStatus() == PayrollRun.Status.POSTED)
        @template.shared.reverseForm(action = "/payroll/" + run.getId().toString() + "/reverse",
            confirmKey = "prun.view.reverseConfirm",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/PAYROLL_RUN/${run.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
