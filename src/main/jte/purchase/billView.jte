@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.Bill
@import com.averpo.erp.purchase.domain.BillLine
@import com.averpo.erp.purchase.domain.BillLineType
@import com.averpo.erp.purchase.domain.BillStatus
@import com.averpo.erp.purchase.domain.PaymentStatus
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param Bill bill
@param String vendorName
@param Map<UUID, String> itemNames
@param Map<UUID, String> warehouseNames
@param Map<UUID, String> accountNames
@param Map<UUID, String> unitNames
@param Map<UUID, String> taxRateNames
@param com.averpo.erp.purchase.domain.PurchaseOrder sourcePurchaseOrder = null
@param java.util.List<com.averpo.erp.purchase.domain.VendorCreditApplication> vendorCreditApplications = java.util.List.of()
@param java.util.List<com.averpo.erp.purchase.domain.VendorCredit> vendorCreditsFromThis = java.util.List.of()
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = bill.getBillNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "bill") ${bill.getBillNumber()}</h1>
        <a href="/bills" class="text-primary underline dark:text-primary-dark">${msg.localize("bill.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада (flex-col), боғлиқ майдонлар
         жуфт-жуфт (Таъминотчи+Фактура, Сана+Муддат, PO+Валюта, Ҳолат+Тўлов,
         Тўланган+Қолдиқ); responsive грид auto-fit ўрнида барқарор 1/2/4 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.vendor")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${vendorName}</span>
        </div>
        @if(bill.getVendorInvoiceNumber() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.vendorInvoice")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${bill.getVendorInvoiceNumber()}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${bill.getBillDate().toString()}</span>
        </div>
        @if(bill.getDueDate() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.dueDate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${bill.getDueDate().toString()}</span>
            </div>
        @endif
        @if(sourcePurchaseOrder != null)
            <%-- «Буюртмадан» белгиси (estimates-po.md LinkedTxn) --%>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("po.linkedFrom")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark"><a href="/purchase-orders/${sourcePurchaseOrder.getId().toString()}" class="text-primary underline dark:text-primary-dark">${sourcePurchaseOrder.getPoNumber()}</a></span>
            </div>
        @endif
        @if(!bill.getCurrency().getCode().equals(homeCurrency))
            <%-- Arbitr-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(bill.getCurrency().getCode(), homeCurrency, bill.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.form.currency")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${bill.getCurrency().getCode()} (${msg.localize("bill.form.rate")}: ${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())})</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = bill.getStatus().name(), msg = msg)</span>
        </div>
        @if(bill.getStatus() == BillStatus.POSTED)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.payStatus")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = bill.getPaymentStatus().name(), family = "paystatus", msg = msg)</span>
            </div>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.view.paid")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(bill.getPaidAmount())} ${bill.getCurrency().getCode()}</span>
            </div>
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bill.col.balance")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(bill.getBalanceDue())} ${bill.getCurrency().getCode()}</span>
            </div>
        @endif
    </div>

    <%-- Arbitr-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да
         (DRAFT'да яширин, ҳали проводкаси йўқ) --%>
    @if(bill.getStatus() == BillStatus.POSTED || bill.getStatus() == BillStatus.REVERSED)
        @template.shared.jeLink(sourceType = "BILL", id = bill.getId().toString(), msg = msg)
    @endif

    @if(bill.getMemo() != null)
        <p class="mb-4">${bill.getMemo()}</p>
    @endif

    @template.shared.table(cls = "min-w-[820px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("bill.row.type")}</th>
            <th>${msg.localize("bill.row.itemOrAccount")}</th>
            <th>${msg.localize("bill.row.warehouse")}</th>
            <th class="text-right">${msg.localize("bill.row.qty")}</th>
            <th class="text-right">${msg.localize("bill.row.price")}</th>
            <th class="text-right">${msg.localize("bill.row.amount")}</th>
            <th class="text-right">${msg.localize("tax.col.tax")}</th>
            <th>${msg.localize("bill.row.memo")}</th>
        </tr>
        </thead>
        <tbody>
        !{java.math.BigDecimal taxSum = java.math.BigDecimal.ZERO;}
        @for(BillLine line : bill.getLines())
            !{taxSum = taxSum.add(line.getTaxAmount());}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${msg.localize("bill.type." + line.getType().name())}</td>
                <td>
                    @if(line.getType() == BillLineType.ITEM)
                        ${itemNames.getOrDefault(line.getItemId(), "-")}
                    @elseif(line.getType() == BillLineType.EXPENSE)
                        ${accountNames.getOrDefault(line.getAccountId(), "-")}
                    @else
                        ${msg.localize("bill.row.landedCostNote")}
                    @endif
                </td>
                <td>
                    @if(line.getWarehouseId() != null)
                        ${warehouseNames.getOrDefault(line.getWarehouseId(), "-")}
                    @endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    <%-- UoM: миқдор киритилган бирлиги билан (U4) --%>
                    @if(line.getQuantity() != null)${Fmt.qty(line.getQuantity(), line.getUnitId() == null ? null : unitNames.get(line.getUnitId()))}@endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getUnitPrice() != null)${Fmt.money(line.getUnitPrice())}@endif
                </td>
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
            <td colspan="7" class="text-right">${msg.localize("tax.subtotal")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(bill.getTotal().subtract(taxSum))}</td>
            <td></td>
        </tr>
        @if(taxSum.signum() > 0)
            <tr>
                <td colspan="7" class="text-right">${msg.localize("tax.col.tax")}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(taxSum)}</td>
                <td></td>
            </tr>
        @endif
        <tr>
            <td colspan="7" class="text-right font-bold">${msg.localize("tax.total")}:</td>
            <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(bill.getTotal())} ${bill.getCurrency().getCode()}</td>
            <td></td>
        </tr>
        @if(!bill.getCurrency().getCode().equals(homeCurrency))
            <tr>
                <td colspan="7" class="text-right">${msg.localize("bill.view.totalBase", homeCurrency)}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(bill.getTotalBase())}</td>
                <td></td>
            </tr>
        @endif
        </tfoot>
    `)

    <%-- Қайтариш интеграцияси (returns.md): қўлланган таъминотчи
         кредитлари balance изоҳи + шу ҳужжатдан яратилган кредитлар --%>
    @if(!vendorCreditApplications.isEmpty() || !vendorCreditsFromThis.isEmpty())
        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("vc.bill.credits")}</h3>
        @template.shared.table(cls = "min-w-[480px]", wrap = "mb-4 max-w-[720px]", c = @`
            <tbody>
            @for(var application : vendorCreditApplications)
                <tr>
                    <td><a href="/vendor-credits/${application.getVendorCredit().getId().toString()}">${application.getVendorCredit().getVcNumber()}</a>
                        <span class="text-current/60">- ${msg.localize("cm.invoice.applied")}</span></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(application.getAmount())} ${bill.getCurrency().getCode()}</td>
                </tr>
            @endfor
            @for(var credit : vendorCreditsFromThis)
                <tr>
                    <td><a href="/vendor-credits/${credit.getId().toString()}">${credit.getVcNumber()}</a>
                        <span class="text-current/60">- ${msg.localize("cm.invoice.createdFrom")}</span></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(credit.getTotal())} ${credit.getCurrency().getCode()}</td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    @if(canEdit != null && canEdit && bill.getStatus() == BillStatus.POSTED)
        <%-- QBO оқими: қайтариш bill'дан очилади - prefill билан --%>
        <p class="mb-4">
            <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
               href="/vendor-credits/new?billId=${bill.getId().toString()}">${msg.localize("cm.invoice.createReturn")}</a>
        </p>
    @endif

    @if(canEdit != null && canEdit && bill.getStatus() == BillStatus.DRAFT)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
            <form method="post" action="/bills/${bill.getId().toString()}/post">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("bill.view.post")}</button>
            </form>
            <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" href="/bills/${bill.getId().toString()}/edit">${msg.localize("bill.view.edit")}</a>
            <form method="post" action="/bills/${bill.getId().toString()}/delete"
                  onsubmit="return confirm('${msg.lookup("bill.view.deleteConfirm")}');">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-4 py-2 text-center text-sm font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("bill.view.delete")}</button>
            </form>
        </div>
    @elseif(canEdit != null && canEdit && bill.getStatus() == BillStatus.POSTED)
        @template.shared.reverseForm(action = "/bills/" + bill.getId().toString() + "/reverse",
            confirmKey = "bill.view.reverseConfirm", labelPrefix = "bill.view",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/BILL/${bill.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
