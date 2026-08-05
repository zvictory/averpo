@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.RefundReceipt
@import com.averpo.erp.sales.domain.RefundReceiptLine
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param RefundReceipt receipt
@param String customerName
@param Map<UUID, String> itemNames
@param Map<UUID, String> unitNames
@param Map<UUID, String> taxRateNames
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

@template.layout.main(title = receipt.getRrNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "refundReceipt") ${receipt.getRrNumber()}</h1>
        <a href="/refund-receipts" class="text-primary underline dark:text-primary-dark">${msg.localize("rr.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.form.customer")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${customerName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("rr.form.bankAccount")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${bankAccountName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("cm.form.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${receipt.getRrDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = receipt.getStatus().name(), msg = msg)</span>
        </div>
        @if(!receipt.getCurrency().getCode().equals(homeCurrency))
            <%-- DEC-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(receipt.getCurrency().getCode(), homeCurrency, receipt.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.form.rate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.total")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(receipt.getTotal())} ${receipt.getCurrency().getCode()}</span>
        </div>
    </div>

    <%-- DEC-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да --%>
    @if(receipt.getStatus() == RefundReceipt.Status.POSTED || receipt.getStatus() == RefundReceipt.Status.REVERSED)
        @template.shared.jeLink(sourceType = "REFUND_RECEIPT", id = receipt.getId().toString(), msg = msg)
    @endif

    @template.shared.table(cls = "min-w-[640px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("sinv.row.item")}</th>
            <th class="text-right">${msg.localize("sinv.row.qty")}</th>
            <th class="text-right">${msg.localize("sinv.row.price")}</th>
            <th class="text-right">${msg.localize("tax.col.tax")}</th>
            <th class="text-right">${msg.localize("bt.col.total")}</th>
        </tr>
        </thead>
        <tbody>
        !{java.math.BigDecimal taxSum = java.math.BigDecimal.ZERO;}
        @for(RefundReceiptLine line : receipt.getLines())
            !{taxSum = taxSum.add(line.getTaxAmount());}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${itemNames.getOrDefault(line.getItemId(), "-")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(line.getQuantity(), line.getUnitId() == null ? null : unitNames.get(line.getUnitId()))}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getUnitPrice())} ${receipt.getCurrency().getCode()}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getTaxAmount().signum() > 0)
                        <span class="text-sm text-current/60">${taxRateNames.getOrDefault(line.getTaxRateId(), "")}</span>
                        ${Fmt.money(line.getTaxAmount())}
                    @endif
                </td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(line.grossAmount())} ${receipt.getCurrency().getCode()}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <%-- Оралиқ жами (net) / ҚҚС / Жами (gross) - docs/modules/tax.md --%>
        <tr>
            <td colspan="5" class="text-right">${msg.localize("tax.subtotal")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(receipt.getTotal().subtract(taxSum))}</td>
        </tr>
        @if(taxSum.signum() > 0)
            <tr>
                <td colspan="5" class="text-right">${msg.localize("tax.col.tax")}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(taxSum)}</td>
            </tr>
        @endif
        <tr>
            <td colspan="5" class="text-right font-bold">${msg.localize("tax.total")}:</td>
            <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(receipt.getTotal())} ${receipt.getCurrency().getCode()}</td>
        </tr>
        </tfoot>
    `)

    @if(receipt.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${receipt.getMemo()}</p>
    @endif

    <%-- Reverse - application йўқ, тўғридан-тўғри сторно --%>
    @if(canEdit != null && canEdit && receipt.getStatus() == RefundReceipt.Status.POSTED)
        @template.shared.reverseForm(action = "/refund-receipts/" + receipt.getId().toString() + "/reverse",
            confirmKey = "rr.view.reverseConfirm",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/REFUND_RECEIPT/${receipt.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
