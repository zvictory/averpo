@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.sales.web.RefundReceiptForm
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param RefundReceiptForm form
@param List<Contact> customers
@param List<Currency> currencies
@param List<Item> items
@param List<Unit> units
@param List<Warehouse> warehouses
@param List<Account> bankAccounts
@param List<TaxRate> taxRates
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<com.averpo.erp.shared.service.TxnClassService.ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

<%-- Пул қайтариш чеки FULL формаси (returns.md, CreditMemo форма
     қолипи + пул счёти). Ҳаволали очилганда (invoiceId hidden)
     сатрлар prefill бўлган. Пул счёти валютаси ҳужжат валютасига
     тенг бўлиши шарт - гаров service'да. --%>
@template.layout.form(title = msg.lookup("rr.form.title"), closeHref = "/refund-receipts", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="rrForm" method="post" action="/refund-receipts">
        @template.shared.csrf(csrf = csrf)
        @if(form.getInvoiceId() != null && !form.getInvoiceId().isBlank())
            <%-- Асл ҳужжат ҳаваласи - қайтим таннархи асл сотувдан (returns.md) --%>
            <input type="hidden" name="invoiceId" value="${form.getInvoiceId()}">
            <p class="mb-3 text-current/60">${msg.localize("cm.form.linkedNote")}</p>
        @endif
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("sinv.form.customer")}
                <%-- Контакт валютаси value input data-currency'сида (087 занжири) --%>
                !{var selCust = customers.stream().filter(c -> c.getId().toString().equals(form.getCustomerId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "customerId", msg = msg,
                        value = form.getCustomerId(),
                        placeholder = msg.lookup("sinv.form.selectCustomer"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/customers/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addCustomer") : null,
                        currencyTarget = "rrCurrency",
                        homeCurrency = homeCurrency,
                        dataCurrency = selCust != null ? (selCust.getCurrency() != null ? selCust.getCurrency().getCode() : homeCurrency) : null,
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("sinv.form.selectCustomer"))
                            @for(Contact customer : customers)
                                @template.shared.comboOption(value = customer.getId().toString(),
                                        label = customer.getDisplayName(),
                                        selected = customer.getId().toString().equals(form.getCustomerId()),
                                        dataCurrency = customer.getCurrency() != null ? customer.getCurrency().getCode() : homeCurrency)
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("rr.form.bankAccount")}
                @template.shared.combobox(name = "bankAccountId", msg = msg,
                        value = form.getBankAccountId(),
                        placeholder = msg.lookup("rr.form.selectBankAccount"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("rr.form.selectBankAccount"))
                            @for(Account account : bankAccounts)
                                @template.shared.comboOption(value = account.getId().toString(),
                                        label = account.getName() + (account.getCurrency() != null ? " (" + account.getCurrency().getCode() + ")" : ""),
                                        selected = account.getId().toString().equals(form.getBankAccountId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("cm.form.date")}
                <input type="date" name="rrDate" required
                       value="${form.getRrDate() != null ? form.getRrDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <%-- Arbitr-097: валюта select + курс майдони - shared.rateBlock
                 компоненти (prefill + кўриниш + кучли-валюта ориентация). --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "rrDate", labelKey = "sinv.form.currency",
                    rateKey = "sinv.form.rate", currencyId = "rrCurrency", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1">${msg.localize("tax.amountsMode")} @template.shared.help(key = "field.vatMode", msg = msg)</span>
                <select name="amountsInclusive" id="rrAmountsMode"
                        class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="false" selected="${!form.isAmountsInclusive()}">${msg.localize("tax.mode.exclusive")}</option>
                    <option value="true" selected="${form.isAmountsInclusive()}">${msg.localize("tax.mode.inclusive")}</option>
                </select>
            </label>
            @if("PER_TXN".equals(classMode))
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("cls.label")}
                    @template.shared.classSelect(classes = classes, msg = msg,
                            name = "classId", selected = form.getClassId(),
                            cls = "mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark")
                </label>
            @endif
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("sinv.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        @template.shared.table(cls = "min-w-[940px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[200px]">${msg.localize("sinv.row.item")}</th>
                <th>${msg.localize("sinv.row.warehouse")}</th>
                <th>${msg.localize("sinv.row.qty")}</th>
                <th>${msg.localize("sinv.row.unit")}</th>
                <th>${msg.localize("sinv.row.price")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
                <th class="text-right">${msg.localize("sinv.row.amount")}</th>
                <th>${msg.localize("tax.col.tax")}</th>
                @if("PER_LINE".equals(classMode))
                    <th>${msg.localize("cls.label")}</th>
                @endif
                <th>${msg.localize("sinv.row.memo")}</th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="lineRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                @template.sales.refundReceiptLineRow(index = i, items = items,
                        units = units, warehouses = warehouses, taxRates = taxRates,
                        msg = msg, line = form.getLines().get(i),
                        classMode = classMode, classes = classes, canEdit = canEdit)
            @endfor
            </tbody>
        `)
        <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark mb-4"
                hx-get="/refund-receipts/line-row"
                hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr")].map(r => 1 + Number(r.dataset.index || 0)))}'
                hx-target="#lineRows" hx-swap="beforeend">${msg.localize("bt.form.addRow")}</button>

        <%-- Arbitr-088 1-банд: жамилар жадвал скролл ўрамидан ТАШҚАРИДА -
             кенг жадвалда қийматлар кесилмайди, доим кўз олдида (QBO Total
             қолипи; expenseForm намунаси). Recompute скрипти id бўйича ёзади -
             id'лар айнан сақланган, скрипт ўзгармаган. --%>
        <div style="display:flex;justify-content:flex-end;margin-bottom:1rem;">
            <div class="min-w-[260px] rounded-radius border border-outline bg-surface-alt px-4 py-3 dark:border-outline-dark dark:bg-surface-dark-alt">
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.subtotal")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="rrSubtotalLive">0.00</span></span>
                </div>
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.col.tax")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="rrTaxLive">0.00</span></span>
                </div>
                <div class="mt-1.5 flex justify-between gap-6 border-t border-outline pt-1.5 font-semibold dark:border-outline-dark">
                    <span>${msg.localize("tax.total")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="rrTotalLive" data-rate-total>0.00</span>
                        <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></span>
                </div>
                <%-- Arbitr-088 4-банд: home жамиси (QBO Total жуфти) - жами × курс,
                     фақат кўрсатиш (server ҳисоби/penny rounding ўз жойида).
                     hidden эмас, display орқали: inline flex hidden'ни енгарди. --%>
                <div id="rrTotalHomeRow" data-rate-home-row style="display:${form.getCurrency() != null && !form.getCurrency().equals(homeCurrency) ? "flex" : "none"}" class="justify-between gap-6 text-sm text-current/60">
                    <span>${msg.localize("tax.totalHome", homeCurrency)}</span>
                    <span id="rrTotalHomeLive" data-rate-home style="white-space:nowrap;font-variant-numeric:tabular-nums;">0.00</span>
                </div>
            </div>
        </div>

        <p class="text-sm text-current/60">
            ${msg.localize("rr.form.note")}
        </p>
    </form>

    <script>
        // Жонли net/ҚҚС/gross (creditMemoForm кўзгуси, Arbitr-053 банд 1) -
        // фақат кўрсатиш; ҳақиқий ҳисоб server'да (returns.md 113-116).
        (function () {
            var body = document.getElementById('lineRows');
            var subEl = document.getElementById('rrSubtotalLive');
            var taxEl = document.getElementById('rrTaxLive');
            var totalEl = document.getElementById('rrTotalLive');
            var modeSel = document.getElementById('rrAmountsMode');
            if (!body || !totalEl) return;
            function num(value) {
                var raw = (value || '').replace(/\s/g, '').replace(',', '.');
                var n = parseFloat(raw);
                return isNaN(n) ? 0 : n;
            }
            function fmt(n) {
                var sign = n < 0 ? '-' : '';
                var parts = Math.abs(n).toFixed(2).split('.');
                return sign + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, String.fromCharCode(160)) + '.' + parts[1];
            }
            function recompute() {
                var inclusive = modeSel && modeSel.value === 'true';
                var subtotal = 0, taxTotal = 0;
                body.querySelectorAll('tr.line').forEach(function (row) {
                    var qty = num((row.querySelector('input[name$=".quantity"]') || {}).value);
                    var price = num((row.querySelector('input[name$=".unitPrice"]') || {}).value);
                    var raw = qty * price;
                    // Сатр суммаси (миқдор × нарх) - view'даги «Сумма» билан айнан
                    var amtCell = row.querySelector('[data-line-amount]');
                    if (amtCell) amtCell.textContent = fmt(raw);
                    var taxSel = row.querySelector('select[data-taxsel]');
                    var opt = taxSel && taxSel.options[taxSel.selectedIndex];
                    var r = (opt && opt.value && opt.dataset.rate) ? parseFloat(opt.dataset.rate) / 100 : 0;
                    var net, tax;
                    if (inclusive) { net = r ? raw / (1 + r) : raw; tax = raw - net; }
                    else { net = raw; tax = raw * r; }
                    subtotal += net; taxTotal += tax;
                });
                if (subEl) subEl.textContent = fmt(subtotal);
                if (taxEl) taxEl.textContent = fmt(taxTotal);
                totalEl.textContent = fmt(subtotal + taxTotal);
            }
            body.addEventListener('input', recompute);
            body.addEventListener('change', recompute);
            if (modeSel) modeSel.addEventListener('change', recompute);
            body.addEventListener('click', function () { setTimeout(recompute, 0); });
            document.body.addEventListener('htmx:afterSwap', recompute);
            recompute();
        })();

        <%-- Arbitr-097: курс prefill + кўриниш/home жами + ориентация -
             rate-block.js компонентида. Иккала эски IIFE ЎЧИРИЛДИ. --%>
</script>
`, actions = @`
    <a href="/refund-receipts" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="rrForm">
            ${msg.localize("cm.form.save")}</button>
    </div>
`)
