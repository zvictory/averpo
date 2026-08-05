@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.domain.PayrollPayment
@import com.averpo.erp.payroll.domain.PayrollPaymentLine
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param PayrollPayment payment
@param Map<UUID, String> employeeNames
@param String accountName
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = payment.getPaypNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "payrollPayment") ${payment.getPaypNumber()}</h1>
        <a href="/payroll/payments" class="text-primary underline dark:text-primary-dark">${msg.localize("payp.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("payp.col.type")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("payp.type." + payment.getPaymentType().name())}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${payment.getPaymentDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("payp.col.account")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${accountName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = payment.getStatus().name(), msg = msg)</span>
        </div>
    </div>

    <%-- Arbitr-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да
         (DRAFT'да яширин). payrollRun'да JE ҳаволаси аллақачон entryId
         орқали бор, шунга у ерга такрор қўшилмайди --%>
    @if(payment.getStatus() == PayrollPayment.Status.POSTED || payment.getStatus() == PayrollPayment.Status.REVERSED)
        @template.shared.jeLink(sourceType = "PAYROLL_PAYMENT", id = payment.getId().toString(), msg = msg)
    @endif

    @if(payment.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${payment.getMemo()}</p>
    @endif

    <p class="mb-2 text-[.9rem] text-current/60">${msg.localize("payp.form.amountsIn", homeCurrency)}</p>
    @template.shared.table(cls = "min-w-[420px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("prun.row.employee")}</th>
            <th class="text-right">${msg.localize("payp.col.amount")}</th>
        </tr>
        </thead>
        <tbody>
        !{int no = 0;}
        @for(PayrollPaymentLine line : payment.getLines())
            !{no++;}
            <tr>
                <td>${no}</td>
                <td>${employeeNames.getOrDefault(line.getEmployeeId(), "-")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getAmount())}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
            <td colspan="2" class="text-right">${msg.localize("prun.form.total")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(payment.getTotal())} ${homeCurrency}</td>
        </tr>
        </tfoot>
    `)

    @if(canEdit != null && canEdit && payment.getStatus() == PayrollPayment.Status.DRAFT)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;align-items:center;">
            <form method="post" action="/payroll/payments/${payment.getId().toString()}/post">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("payp.view.post")}</button>
            </form>
            <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" href="/payroll/payments/${payment.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
            <form method="post" action="/payroll/payments/${payment.getId().toString()}/delete"
                  onsubmit="return confirm('${msg.lookup("payp.view.deleteConfirm")}');">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-4 py-2 text-center text-sm font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("common.delete")}</button>
            </form>
        </div>
    @elseif(canEdit != null && canEdit && payment.getStatus() == PayrollPayment.Status.POSTED)
        @template.shared.reverseForm(action = "/payroll/payments/" + payment.getId().toString() + "/reverse",
            confirmKey = "payp.view.reverseConfirm",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/PAYROLL_PAYMENT/${payment.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
