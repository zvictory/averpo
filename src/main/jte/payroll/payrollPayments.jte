@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.domain.PayrollPayment
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<PayrollPayment> payments
@param Map<UUID, String> accountNames
@param org.springframework.data.domain.Page<PayrollPayment> page = null
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.payrollPayments", title = msg.lookup("payp.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("payp.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/payroll/payments/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    @if(payments.isEmpty())
        <p class="text-current/60">${msg.localize("payp.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[760px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bt.col.number")}</th>
                <th>${msg.localize("payp.col.type")}</th>
                <th>${msg.localize("bt.col.date")}</th>
                <th>${msg.localize("payp.col.account")}</th>
                <th class="text-right">${msg.localize("bt.col.total")}</th>
                <th>${msg.localize("bt.col.status")}</th>
            </tr>
            </thead>
            <tbody>
            @for(PayrollPayment payment : payments)
                <%-- DRAFT - таҳрир формасига, акс ҳолда кўришга --%>
                <tr data-href="${payment.getStatus() == PayrollPayment.Status.DRAFT ? "/payroll/payments/" + payment.getId() + "/edit" : "/payroll/payments/" + payment.getId()}">
                    <td><a href="/payroll/payments/${payment.getId().toString()}">${payment.getPaypNumber()}</a></td>
                    <td>${msg.localize("payp.type." + payment.getPaymentType().name())}</td>
                    <td>${payment.getPaymentDate().toString()}</td>
                    <td>${accountNames.getOrDefault(payment.getAccountId(), "-")}</td>
                    <td class="text-right whitespace-nowrap">${Fmt.money(payment.getTotal())} ${homeCurrency}</td>
                    <td>
                        @template.shared.badge(value = payment.getStatus().name(), msg = msg)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш - рўйхат саҳифаланган туғилади (Beruniy-perf1 қолипи) --%>
    @template.shared.pagination(page = page, baseUrl = "/payroll/payments", msg = msg)
`)
