@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.Invoice
@import com.averpo.erp.sales.domain.InvoiceLine
@import com.averpo.erp.sales.domain.InvoiceLineType
@import com.averpo.erp.sales.domain.InvoicePaymentAllocation
@import com.averpo.erp.sales.domain.InvoiceStatus
@import com.averpo.erp.sales.service.InvoiceService
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param Invoice invoice
@param String customerName
@param Map<UUID, String> itemNames
@param Map<UUID, String> warehouseNames
@param Map<UUID, String> unitNames
@param Map<UUID, String> taxRateNames
@param List<InvoicePaymentAllocation> allocations
@param List<com.averpo.erp.sales.domain.CreditApplication> creditApplications = java.util.List.of()
@param List<com.averpo.erp.sales.domain.CreditMemo> creditsFromThis = java.util.List.of()
@param InvoiceService.CreditCheck creditCheck
@param com.averpo.erp.sales.domain.Estimate sourceEstimate = null
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = invoice.getInvoiceNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "invoice") ${invoice.getInvoiceNumber()}</h1>
        <a href="/invoices" class="text-primary underline dark:text-primary-dark">${msg.localize("sinv.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Credit limit огоҳлантириши - тўсмайди (QBO услуби, лойиҳа қарори) --%>
    @if(creditCheck != null && creditCheck.exceeded())
        <p class="mb-4 rounded-radius border border-warning bg-warning/10 px-3 py-2.5 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("sinv.creditWarning", Fmt.money(creditCheck.exposure()), Fmt.money(creditCheck.creditLimit()), invoice.getCurrency().getCode())}
        </p>
    @endif

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.col.customer")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${customerName}</span>
        </div>
        @if(sourceEstimate != null)
            <%-- «Estimate'дан» белгиси (estimates-po.md LinkedTxn) --%>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("est.linkedFrom")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark"><a href="/estimates/${sourceEstimate.getId().toString()}" class="text-primary underline dark:text-primary-dark">${sourceEstimate.getEstimateNumber()}</a></span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${invoice.getInvoiceDate().toString()}</span>
        </div>
        @if(invoice.getDueDate() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.col.dueDate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${invoice.getDueDate().toString()}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = invoice.getStatus().name(), msg = msg)</span>
        </div>
        @if(!invoice.getCurrency().getCode().equals(homeCurrency))
            <%-- DEC-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(invoice.getCurrency().getCode(), homeCurrency, invoice.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.form.currency")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${invoice.getCurrency().getCode()} (${msg.localize("sinv.form.rate")}: ${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())})</span>
            </div>
        @endif
        @if(invoice.getStatus() == InvoiceStatus.POSTED)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.col.payStatus")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = invoice.getPaymentStatus().name(), family = "paystatus", msg = msg)</span>
            </div>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.view.paid")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(invoice.getPaidAmount())} ${invoice.getCurrency().getCode()}</span>
            </div>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.col.balance")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(invoice.getBalanceDue())} ${invoice.getCurrency().getCode()}</span>
            </div>
        @endif
    </div>

    <%-- DEC-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да
         (DRAFT'да яширин, ҳали проводкаси йўқ); by-source статик URL --%>
    @if(invoice.getStatus() == InvoiceStatus.POSTED || invoice.getStatus() == InvoiceStatus.REVERSED)
        @template.shared.jeLink(sourceType = "INVOICE", id = invoice.getId().toString(), msg = msg)
    @endif

    @if(invoice.getMemo() != null)
        <p class="mb-4">${invoice.getMemo()}</p>
    @endif

    @template.shared.table(cls = "min-w-[760px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("sinv.row.item")}</th>
            <th>${msg.localize("sinv.row.warehouse")}</th>
            <th class="text-right">${msg.localize("sinv.row.qty")}</th>
            <th class="text-right">${msg.localize("sinv.row.price")}</th>
            <th class="text-right">${msg.localize("sinv.row.amount")}</th>
            <th class="text-right">${msg.localize("tax.col.tax")}</th>
            <th>${msg.localize("sinv.row.memo")}</th>
        </tr>
        </thead>
        <tbody>
        !{java.math.BigDecimal taxSum = java.math.BigDecimal.ZERO;}
        @for(InvoiceLine line : invoice.getLines())
            !{taxSum = taxSum.add(line.getTaxAmount());}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${itemNames.getOrDefault(line.getItemId(), "-")}</td>
                <td>
                    @if(line.getType() == InvoiceLineType.ITEM && line.getWarehouseId() != null)
                        ${warehouseNames.getOrDefault(line.getWarehouseId(), "-")}
                    @endif
                </td>
                <%-- UoM: миқдор киритилган бирлиги билан (U4) --%>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(line.getQuantity(), line.getUnitId() == null ? null : unitNames.get(line.getUnitId()))}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getUnitPrice())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getAmount())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getTaxAmount().signum() > 0)
                        <span class="text-sm text-current/60">${taxRateNames.getOrDefault(line.getTaxRateId(), "")}</span>
                        ${Fmt.money(line.getTaxAmount())}
                    @endif
                </td>
                <td>${line.getMemo()}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <%-- Оралиқ жами (net) / ҚҚС / Жами (gross) - docs/modules/tax.md --%>
        <tr>
            <td colspan="6" class="text-right">${msg.localize("tax.subtotal")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(invoice.getTotal().subtract(taxSum))}</td>
            <td></td>
        </tr>
        @if(taxSum.signum() > 0)
            <tr>
                <td colspan="6" class="text-right">${msg.localize("tax.col.tax")}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(taxSum)}</td>
                <td></td>
            </tr>
        @endif
        <tr>
            <td colspan="6" class="text-right font-bold">${msg.localize("tax.total")}:</td>
            <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(invoice.getTotal())} ${invoice.getCurrency().getCode()}</td>
            <td></td>
        </tr>
        @if(!invoice.getCurrency().getCode().equals(homeCurrency))
            <tr>
                <td colspan="6" class="text-right">${msg.localize("sinv.view.totalBase", homeCurrency)}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(invoice.getTotalBase())}</td>
                <td></td>
            </tr>
        @endif
        </tfoot>
    `)

    @if(!allocations.isEmpty())
        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("sinv.view.payments")}</h3>
        @template.shared.table(cls = "min-w-[480px]", wrap = "mb-4 max-w-[720px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("rcpt.col.number")}</th>
                <th class="text-right">${msg.localize("rcpt.view.allocAmount")}</th>
            </tr>
            </thead>
            <tbody>
            @for(InvoicePaymentAllocation allocation : allocations)
                <tr>
                    <td><a href="/invoice-payments/${allocation.getPayment().getId().toString()}">${allocation.getPayment().getReceiptNumber()}</a></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(allocation.getAmount())} ${invoice.getCurrency().getCode()}</td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    <%-- Қайтариш интеграцияси (returns.md): қўлланган кредитлар balance
         изоҳи + шу ҳужжатдан яратилган кредит-ноталар --%>
    @if(!creditApplications.isEmpty() || !creditsFromThis.isEmpty())
        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("cm.invoice.credits")}</h3>
        @template.shared.table(cls = "min-w-[480px]", wrap = "mb-4 max-w-[720px]", c = @`
            <tbody>
            @for(var application : creditApplications)
                <tr>
                    <td><a href="/credit-memos/${application.getCreditMemo().getId().toString()}">${application.getCreditMemo().getCmNumber()}</a>
                        <span class="text-current/60">- ${msg.localize("cm.invoice.applied")}</span></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(application.getAmount())} ${invoice.getCurrency().getCode()}</td>
                </tr>
            @endfor
            @for(var credit : creditsFromThis)
                <tr>
                    <td><a href="/credit-memos/${credit.getId().toString()}">${credit.getCmNumber()}</a>
                        <span class="text-current/60">- ${msg.localize("cm.invoice.createdFrom")}</span></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(credit.getTotal())} ${credit.getCurrency().getCode()}</td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    @if(canEdit != null && canEdit && invoice.getStatus() == InvoiceStatus.POSTED)
        <%-- QBO оқими: қайтариш invoice'дан очилади - prefill билан --%>
        <p class="mb-4">
            <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
               href="/credit-memos/new?invoiceId=${invoice.getId().toString()}">${msg.localize("cm.invoice.createReturn")}</a>
        </p>
    @endif

    @if(canEdit != null && canEdit && invoice.getStatus() == InvoiceStatus.DRAFT)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
            <form method="post" action="/invoices/${invoice.getId().toString()}/post">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("sinv.view.post")}</button>
            </form>
            <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
               href="/invoices/${invoice.getId().toString()}/edit">${msg.localize("sinv.view.edit")}</a>
            <form method="post" action="/invoices/${invoice.getId().toString()}/delete"
                  onsubmit="return confirm('${msg.lookup("sinv.view.deleteConfirm")}');">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-4 py-2 text-center text-sm font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("sinv.view.delete")}</button>
            </form>
        </div>
    @elseif(canEdit != null && canEdit && invoice.getStatus() == InvoiceStatus.POSTED)
        @template.shared.reverseForm(action = "/invoices/" + invoice.getId().toString() + "/reverse",
            confirmKey = "sinv.view.reverseConfirm", labelPrefix = "sinv.view",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/INVOICE/${invoice.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
