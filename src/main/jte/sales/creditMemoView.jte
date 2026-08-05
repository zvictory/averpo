@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.CreditApplication
@import com.averpo.erp.sales.domain.CreditMemo
@import com.averpo.erp.sales.domain.CreditMemoLine
@import com.averpo.erp.sales.domain.Invoice
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param CreditMemo memo
@param String customerName
@param Map<UUID, String> itemNames
@param Map<UUID, String> unitNames
@param Map<UUID, String> taxRateNames
@param List<CreditApplication> applications
@param List<Invoice> openInvoices
@param String homeCurrency
@param String today
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = memo.getCmNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "creditMemo") ${memo.getCmNumber()}</h1>
        <a href="/credit-memos" class="text-primary underline dark:text-primary-dark">${msg.localize("cm.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.form.customer")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${customerName}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("cm.form.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${memo.getCmDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = memo.getStatus().name(), msg = msg)</span>
        </div>
        @if(!memo.getCurrency().getCode().equals(homeCurrency))
            <%-- DEC-065: курс доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
            !{var rateOr = Fmt.orient(memo.getCurrency().getCode(), homeCurrency, memo.getExchangeRate());}
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("sinv.form.rate")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.total")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(memo.getTotal())} ${memo.getCurrency().getCode()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("cm.col.applied")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(memo.getAppliedAmount())} ${memo.getCurrency().getCode()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("cm.col.open")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(memo.getOpenBalance())} ${memo.getCurrency().getCode()}</span>
        </div>
    </div>

    <%-- DEC-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да --%>
    @if(memo.getStatus() == CreditMemo.Status.POSTED || memo.getStatus() == CreditMemo.Status.REVERSED)
        @template.shared.jeLink(sourceType = "CREDIT_MEMO", id = memo.getId().toString(), msg = msg)
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
        @for(CreditMemoLine line : memo.getLines())
            !{taxSum = taxSum.add(line.getTaxAmount());}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${itemNames.getOrDefault(line.getItemId(), "-")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(line.getQuantity(), line.getUnitId() == null ? null : unitNames.get(line.getUnitId()))}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getUnitPrice())} ${memo.getCurrency().getCode()}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getTaxAmount().signum() > 0)
                        <span class="text-sm text-current/60">${taxRateNames.getOrDefault(line.getTaxRateId(), "")}</span>
                        ${Fmt.money(line.getTaxAmount())}
                    @endif
                </td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(line.grossAmount())} ${memo.getCurrency().getCode()}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <%-- Оралиқ жами (net) / ҚҚС / Жами (gross) - docs/modules/tax.md --%>
        <tr>
            <td colspan="5" class="text-right">${msg.localize("tax.subtotal")}:</td>
            <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(memo.getTotal().subtract(taxSum))}</td>
        </tr>
        @if(taxSum.signum() > 0)
            <tr>
                <td colspan="5" class="text-right">${msg.localize("tax.col.tax")}:</td>
                <td class="text-right whitespace-nowrap tabular-nums">${Fmt.money(taxSum)}</td>
            </tr>
        @endif
        <tr>
            <td colspan="5" class="text-right font-bold">${msg.localize("tax.total")}:</td>
            <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(memo.getTotal())} ${memo.getCurrency().getCode()}</td>
        </tr>
        </tfoot>
    `)

    @if(memo.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${memo.getMemo()}</p>
    @endif

    <%-- Қўллашлар рўйхати + unapply --%>
    @if(!applications.isEmpty())
        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("cm.view.applications")}</h3>
        @template.shared.table(cls = "min-w-[520px]", wrap = "mb-4 max-w-[760px]", c = @`
            <tbody>
            @for(CreditApplication application : applications)
                <tr>
                    <td><a href="/invoices/${application.getInvoice().getId().toString()}">${application.getInvoice().getInvoiceNumber()}</a></td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(application.getAmount())} ${memo.getCurrency().getCode()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                        @if(canEdit != null && canEdit && memo.getStatus() == CreditMemo.Status.POSTED)
                            <form method="post" action="/credit-memos/${memo.getId().toString()}/unapply" style="display:inline;">
                                @template.shared.csrf(csrf = csrf)
                                <input type="hidden" name="applicationId" value="${application.getId().toString()}">
                                <input type="hidden" name="reversalDate" value="${today}">
                                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit">${msg.localize("cm.view.unapply")}</button>
                            </form>
                        @endif
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    <%-- «Қўллаш» бўлими: мижознинг очиқ invoice'лари (бир хил валютада) --%>
    @if(canEdit != null && canEdit && memo.getStatus() == CreditMemo.Status.POSTED
            && memo.getOpenBalance().signum() > 0)
        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("cm.view.applyTitle")} @template.shared.help(key = "field.creditApply", msg = msg)</h3>
        @if(openInvoices.isEmpty())
            <p class="mb-4 text-current/60">${msg.localize("cm.view.noOpenInvoices")}</p>
        @else
            <form method="post" action="/credit-memos/${memo.getId().toString()}/apply"
                  class="mb-4 flex max-w-[720px] flex-wrap items-end gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
                @template.shared.csrf(csrf = csrf)
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[220px] flex-[2]">${msg.localize("cm.view.invoice")}
                    <select name="invoiceId" required id="cmApplyInvoice" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        @for(Invoice invoice : openInvoices)
                            <option value="${invoice.getId().toString()}" data-due="${invoice.getBalanceDue()}">
                                ${invoice.getInvoiceNumber()} (${Fmt.money(invoice.getBalanceDue())} ${invoice.getCurrency().getCode()})
                            </option>
                        @endfor
                    </select>
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("cm.view.applyAmount")}
                    <input name="amount" required inputmode="decimal" size="12" id="cmApplyAmount"
                           class="mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                </label>
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("cm.view.apply")}</button>
            </form>
            <script>
                // BA-046: invoice танланганда сумма prefill -
                // min(кредит очиқ қолдиғи, invoice қарзи). Қўлда терилган
                // қиймат устидан ёзилмайди (srRate data-autofill нақши);
                // string қиймат айнан кўчирилади - float яхлитлаш йўқ.
                (function () {
                    var OPEN = "${memo.getOpenBalance()}";
                    var select = document.getElementById('cmApplyInvoice');
                    var amount = document.getElementById('cmApplyAmount');
                    if (!select || !amount) return;
                    function canAutofill() {
                        return amount.value.trim() === '' || amount.dataset.autofill === '1';
                    }
                    function prefill() {
                        if (!canAutofill()) return;
                        var opt = select.options[select.selectedIndex];
                        if (!opt || !opt.dataset.due) return;
                        amount.value = parseFloat(opt.dataset.due) < parseFloat(OPEN)
                                ? opt.dataset.due : OPEN;
                        amount.dataset.autofill = '1';
                    }
                    amount.addEventListener('input', function () { amount.dataset.autofill = ''; });
                    select.addEventListener('change', prefill);
                    prefill();
                })();
            </script>
        @endif
    @endif

    <%-- Reverse (BR-RET-007: қўлланган бўлса service рад этади) --%>
    @if(canEdit != null && canEdit && memo.getStatus() == CreditMemo.Status.POSTED)
        @template.shared.reverseForm(action = "/credit-memos/" + memo.getId().toString() + "/reverse",
            confirmKey = "cm.view.reverseConfirm",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/CREDIT_MEMO/${memo.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
