@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.Bill
@import com.averpo.erp.purchase.domain.BillPayment
@import com.averpo.erp.purchase.domain.BillPaymentAllocation
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param BillPayment payment
@param List<BillPaymentAllocation> allocations
@param List<Bill> allocatable
@param String vendorName
@param String bankAccountName
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = payment.getPaymentNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "billPayment") ${payment.getPaymentNumber()}</h1>
        <a href="/payments" class="text-primary underline dark:text-primary-dark">${msg.localize("pay.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.col.vendor")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${vendorName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${payment.getPaymentDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.form.bankAccount")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${bankAccountName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = payment.getStatus().name(), msg = msg)</span>
        </div>
        <%-- U2: чет валютали тўловда валюта ва курс кўрсатилади (billView услуби) --%>
        @if(!payment.getCurrency().getCode().equals(homeCurrency))
            <%-- Arbitr-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(payment.getCurrency().getCode(), homeCurrency, payment.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.form.currency")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${payment.getCurrency().getCode()} (${msg.localize("bill.form.rate")}: ${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())})</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.col.total")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(payment.getTotalAmount())} ${payment.getCurrency().getCode()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.col.allocated")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(payment.getAllocatedAmount())} ${payment.getCurrency().getCode()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("pay.col.unallocated")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">
                @if(payment.getUnallocatedAmount().signum() > 0)
                    <span class="font-semibold text-amber-700 dark:text-amber-500">${Fmt.money(payment.getUnallocatedAmount())} ${payment.getCurrency().getCode()}</span>
                @else
                    ${Fmt.money(payment.getUnallocatedAmount())} ${payment.getCurrency().getCode()}
                @endif
            </span>
        </div>
    </div>

    <%-- Arbitr-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да --%>
    @if(payment.getStatus() == BillPayment.Status.POSTED || payment.getStatus() == BillPayment.Status.REVERSED)
        @template.shared.jeLink(sourceType = "BILL_PAYMENT", id = payment.getId().toString(), msg = msg)
    @endif

    @if(payment.getMemo() != null)
        <p class="mb-4">${payment.getMemo()}</p>
    @endif

    <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("pay.view.allocations")}</h3>
    @if(allocations.isEmpty())
        <p class="mb-4 text-current/60">${msg.localize("pay.view.noAllocations")}</p>
    @else
        @template.shared.table(cls = "min-w-[480px]", wrap = "mb-4 max-w-[720px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bill.col.number")}</th>
                <th class="text-right">${msg.localize("pay.view.allocAmount")}</th>
            </tr>
            </thead>
            <tbody>
            @for(BillPaymentAllocation allocation : allocations)
                <tr>
                    <td><a href="/bills/${allocation.getBill().getId().toString()}">${allocation.getBill().getBillNumber()}</a></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(allocation.getAmount())} ${payment.getCurrency().getCode()}</td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    @if(canEdit != null && canEdit && !allocatable.isEmpty())
        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("pay.view.useAdvance")}</h3>
        <form method="post" action="/payments/${payment.getId().toString()}/allocate"
              style="margin-bottom:1.25rem;">
            @template.shared.csrf(csrf = csrf)
            @template.shared.table(cls = "min-w-[760px]", wrap = "mb-2.5", c = @`
                <thead>
                <tr>
                    <th>${msg.localize("bill.col.number")}</th>
                    <th>${msg.localize("bill.col.date")}</th>
                    <th>${msg.localize("bill.col.dueDate")}</th>
                    <th class="text-right">${msg.localize("bill.col.total")}</th>
                    <th class="text-right">${msg.localize("bill.col.balance")}</th>
                    <th class="min-w-[130px]">${msg.localize("pay.form.allocate")} <span class="font-normal text-current/50">${payment.getCurrency().getCode()}</span></th>
                </tr>
                </thead>
                <tbody>
                    @template.purchase.paymentOpenBills(openBills = allocatable, msg = msg)
                </tbody>
            `)
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("pay.view.allocateBtn")}</button>
        </form>
    @endif

    @if(canEdit != null && canEdit && payment.getStatus() == BillPayment.Status.POSTED)
        @template.shared.reverseForm(action = "/payments/" + payment.getId().toString() + "/reverse",
            confirmKey = "pay.view.reverseConfirm", labelPrefix = "bill.view",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/PAYMENT/${payment.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
