@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.PurchaseOrder
@import com.averpo.erp.purchase.domain.PurchaseOrderLine
@import com.averpo.erp.purchase.domain.PurchaseOrderStatus
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param PurchaseOrder po
@param String vendorName
@param Map<UUID, String> itemNames
@param Map<UUID, String> unitNames
@param Map<UUID, String> taxRateNames
@param String homeCurrency
@param String linkedBillNumber = null
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = po.getPoNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "purchaseOrder") ${po.getPoNumber()}</h1>
        <a href="/purchase-orders" class="text-primary underline dark:text-primary-dark">${msg.localize("po.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.col.vendor")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${vendorName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${po.getPoDate().toString()}</span>
        </div>
        @if(po.getExpectedDate() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.col.expected")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${po.getExpectedDate().toString()}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = po.getStatus().name(), family = "po.status", msg = msg)</span>
        </div>
        @if(!po.getCurrency().getCode().equals(homeCurrency))
            <%-- Arbitr-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(po.getCurrency().getCode(), homeCurrency, po.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.form.currency")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${po.getCurrency().getCode()} (${msg.localize("po.form.rate")}: ${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())})</span>
            </div>
        @endif
        @if(linkedBillNumber != null && po.getBillId() != null)
            <%-- Linked ҳужжат ҳаволаси (LinkedTxn) --%>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.view.linkedBill")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark"><a href="/bills/${po.getBillId().toString()}" class="text-primary underline dark:text-primary-dark">${linkedBillNumber}</a></span>
            </div>
        @endif
    </div>

    <%-- Arbitr-153: PO GL'сиз (non-posting) ҳужжат - молиявий ҳаракат
         тегишли Bill'да ёзилади; шу боис бу экранда сторно/таҳрир йўқ
         (BR-PO-002/003). Битта хира қатор ҳинт - фойдаланувчи «нега
         сторно йўқ» деб ажабланмасин. --%>
    <p class="mb-4 text-sm text-current/60">${msg.localize("po.view.glHint")}</p>

    @if(po.getMemo() != null)
        <p class="mb-4">${po.getMemo()}</p>
    @endif

    @template.shared.table(cls = "min-w-[700px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("po.row.item")}</th>
            <th class="text-right">${msg.localize("po.row.qty")}</th>
            <th class="text-right">${msg.localize("po.row.price")}</th>
            <th class="text-right">${msg.localize("po.row.amount")}</th>
            <th class="text-right">${msg.localize("tax.col.tax")}</th>
            <th>${msg.localize("po.row.memo")}</th>
        </tr>
        </thead>
        <tbody>
        !{java.math.BigDecimal taxSum = java.math.BigDecimal.ZERO;}
        @for(PurchaseOrderLine line : po.getLines())
            !{taxSum = taxSum.add(line.getTaxAmount());}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${itemNames.getOrDefault(line.getItemId(), "-")}</td>
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
        <tr>
            <td colspan="4" class="text-right">${msg.localize("tax.subtotal")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(po.getTotal().subtract(taxSum))}</td>
            <td colspan="2"></td>
        </tr>
        @if(taxSum.signum() > 0)
            <tr>
                <td colspan="4" class="text-right">${msg.localize("tax.col.tax")}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(taxSum)}</td>
                <td colspan="2"></td>
            </tr>
        @endif
        <tr>
            <td colspan="4" class="text-right font-bold">${msg.localize("tax.total")}:</td>
            <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(po.getTotal())} ${po.getCurrency().getCode()}</td>
            <td colspan="2"></td>
        </tr>
        </tfoot>
    `)

    @if(canEdit != null && canEdit)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
            @if(po.getStatus() == PurchaseOrderStatus.OPEN)
                <%-- Айлантириш: bill формаси prefill билан очилади --%>
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" href="/bills/new?purchaseOrderId=${po.getId().toString()}">
                    ${msg.localize("po.view.convert")}</a>
                <form method="post" action="/purchase-orders/${po.getId().toString()}/status">
                    @template.shared.csrf(csrf = csrf)
                    <input type="hidden" name="status" value="CLOSED">
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit">${msg.localize("po.view.close")}</button>
                </form>
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" href="/purchase-orders/${po.getId().toString()}/edit">${msg.localize("po.view.edit")}</a>
            @elseif(po.getBillId() == null)
                <%-- Linked'сиз CLOSED: қайта очиш мумкин --%>
                <form method="post" action="/purchase-orders/${po.getId().toString()}/status">
                    @template.shared.csrf(csrf = csrf)
                    <input type="hidden" name="status" value="OPEN">
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("po.view.reopen")}</button>
                </form>
            @endif
            @if(po.getBillId() == null)
                <form method="post" action="/purchase-orders/${po.getId().toString()}/delete"
                      onsubmit="return confirm('${msg.lookup("po.view.deleteConfirm")}');">
                    @template.shared.csrf(csrf = csrf)
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-4 py-2 text-center text-sm font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("po.view.delete")}</button>
                </form>
            @endif
        </div>
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/PURCHASE_ORDER/${po.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
