@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.purchase.web.PurchaseOrderForm
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param PurchaseOrderForm form
@param List<Contact> vendors
@param List<Currency> currencies
@param List<Item> items
@param List<Unit> units
@param List<TaxRate> taxRates
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("po.form.title"), closeHref = "/purchase-orders", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="poForm" method="post" action="/purchase-orders">
        @template.shared.csrf(csrf = csrf)
        @if(form.getId() != null && !form.getId().isBlank())
            <input type="hidden" name="id" value="${form.getId()}">
        @endif
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("po.form.vendor")}
                <%-- Контакт валютаси value input data-currency'сида (087 занжири) --%>
                !{var selVendor = vendors.stream().filter(v -> v.getId().toString().equals(form.getVendorId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "vendorId", msg = msg,
                        value = form.getVendorId(),
                        placeholder = msg.lookup("po.form.selectVendor"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/vendors/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addVendor") : null,
                        currencyTarget = "poCurrency",
                        homeCurrency = homeCurrency,
                        dataCurrency = selVendor != null ? (selVendor.getCurrency() != null ? selVendor.getCurrency().getCode() : homeCurrency) : null,
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("po.form.selectVendor"))
                            @for(Contact vendor : vendors)
                                @template.shared.comboOption(value = vendor.getId().toString(),
                                        label = vendor.getDisplayName(),
                                        selected = vendor.getId().toString().equals(form.getVendorId()),
                                        dataCurrency = vendor.getCurrency() != null ? vendor.getCurrency().getCode() : homeCurrency)
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("po.form.date")}
                <input type="date" name="poDate" required
                       value="${form.getPoDate() != null ? form.getPoDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("po.form.expected")}
                <input type="date" name="expectedDate"
                       value="${form.getExpectedDate() != null ? form.getExpectedDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <%-- DEC-097: валюта select + курс майдони - shared.rateBlock
                 компоненти (prefill + кўриниш + кучли-валюта ориентация). --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "poDate", labelKey = "po.form.currency",
                    rateKey = "po.form.rate", currencyId = "poCurrency", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <%-- ҚҚС нарх режими (tax.md) - фақат жонли кўрсатиш, GL йўқ --%>
                <span class="inline-flex items-center gap-1">${msg.localize("tax.amountsMode")} @template.shared.help(key = "field.vatMode", msg = msg)</span>
                <select name="amountsInclusive" id="poAmountsMode"
                        class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="false" selected="${!form.isAmountsInclusive()}">${msg.localize("tax.mode.exclusive")}</option>
                    <option value="true" selected="${form.isAmountsInclusive()}">${msg.localize("tax.mode.inclusive")}</option>
                </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("po.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        @template.shared.table(cls = "min-w-[820px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[200px]">${msg.localize("po.row.item")}</th>
                <th>${msg.localize("po.row.qty")}</th>
                <th>${msg.localize("po.row.unit")}</th>
                <th>${msg.localize("po.row.price")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
                <th class="text-right">${msg.localize("po.row.amount")}</th>
                <th>${msg.localize("tax.col.tax")}</th>
                <th>${msg.localize("po.row.memo")}</th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="lineRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                @template.purchase.purchaseOrderLineRow(index = i, items = items,
                        units = units, taxRates = taxRates, msg = msg,
                        line = form.getLines().get(i))
            @endfor
            </tbody>
        `)

        <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark mb-4"
                hx-get="/purchase-orders/line-row"
                hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr")].map(r => 1 + Number(r.dataset.index || 0)))}'
                hx-target="#lineRows" hx-swap="beforeend">${msg.localize("po.form.addRow")}</button>

        <%-- DEC-088 1-банд: жамилар жадвал скролл ўрамидан ТАШҚАРИДА -
             кенг жадвалда қийматлар кесилмайди, доим кўз олдида (QBO Total
             қолипи; expenseForm намунаси). Recompute скрипти id бўйича ёзади -
             id'лар айнан сақланган, скрипт ўзгармаган. --%>
        <div style="display:flex;justify-content:flex-end;margin-bottom:1rem;">
            <div class="min-w-[260px] rounded-radius border border-outline bg-surface-alt px-4 py-3 dark:border-outline-dark dark:bg-surface-dark-alt">
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.subtotal")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="poSubtotalLive">0.00</span></span>
                </div>
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.col.tax")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="poTaxLive">0.00</span></span>
                </div>
                <div class="mt-1.5 flex justify-between gap-6 border-t border-outline pt-1.5 font-semibold dark:border-outline-dark">
                    <span>${msg.localize("tax.total")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="poTotalLive" data-rate-total>0.00</span>
                        <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></span>
                </div>
                <%-- DEC-088 4-банд: home жамиси (QBO Total жуфти) - жами × курс,
                     фақат кўрсатиш (server ҳисоби/penny rounding ўз жойида).
                     hidden эмас, display орқали: inline flex hidden'ни енгарди. --%>
                <div id="poTotalHomeRow" data-rate-home-row style="display:${form.getCurrency() != null && !form.getCurrency().equals(homeCurrency) ? "flex" : "none"}" class="justify-between gap-6 text-sm text-current/60">
                    <span>${msg.localize("tax.totalHome", homeCurrency)}</span>
                    <span id="poTotalHomeLive" data-rate-home style="white-space:nowrap;font-variant-numeric:tabular-nums;">0.00</span>
                </div>
            </div>
        </div>

        <p class="text-sm text-current/60">
            ${msg.localize("po.form.homeNote", homeCurrency)}
        </p>
    </form>

    <script>
        // UoM + нарх prefill (billForm услуби): item танланганда бирлик
        // фильтри, каталог ХАРИД нархи × factor (home валютада), item
        // default харид ҚҚС ставкаси.
        (function () {
            var META = {
                @for(Item item : items)
                    "${item.getId().toString()}": {
                        base: "${item.getUnit() == null ? "" : item.getUnit().getId().toString()}",
                        group: "${item.getUnit() == null || item.getUnit().getGroup() == null ? "" : item.getUnit().getGroup().getId().toString()}",
                        def: "${item.getPurchaseUnit() != null ? item.getPurchaseUnit().getId().toString() : (item.getUnit() == null ? "" : item.getUnit().getId().toString())}",
                        price: "${item.getPurchaseCost() == null ? "" : item.getPurchaseCost().stripTrailingZeros().toPlainString()}",
                        ptax: "${item.getPurchaseTaxRateId() == null ? "" : item.getPurchaseTaxRateId().toString()}"
                    },
                @endfor
            };
            function rowOf(el) { return el.closest('tr.line'); }
            function metaOf(row) {
                var itemSel = row.querySelector('[data-itemsel]');
                return (itemSel && META[itemSel.value]) || null;
            }
            function filterUnits(row, resetSelection) {
                var unitSel = row.querySelector('select[data-unitsel]');
                if (!unitSel) return;
                var meta = metaOf(row);
                for (var i = 0; i < unitSel.options.length; i++) {
                    var option = unitSel.options[i];
                    if (!option.value) continue;
                    var ok = meta && ((meta.base && option.value === meta.base)
                            || (meta.group && option.dataset.group === meta.group));
                    option.hidden = !ok;
                    option.disabled = !ok;
                }
                if (resetSelection) unitSel.value = meta ? meta.def : '';
                else if (unitSel.options[unitSel.selectedIndex]
                        && unitSel.options[unitSel.selectedIndex].disabled) unitSel.value = '';
            }
            function prefillPrice(row, overwrite) {
                var meta = metaOf(row);
                var priceInput = row.querySelector('input[name$=".unitPrice"]');
                if (!meta || !meta.price || !priceInput) return;
                if (!overwrite && priceInput.value.trim() !== '') return;
                // IFRS-014: каталог нархи HOME валютада - чет валютали
                // ҳужжатга қуйилмайди
                var currency = ((document.getElementById('poCurrency') || {}).value || '').trim().toUpperCase();
                if (currency && currency !== "${homeCurrency}") return;
                var unitSel = row.querySelector('select[data-unitsel]');
                var option = unitSel && unitSel.options[unitSel.selectedIndex];
                var factor = option && option.value ? parseFloat(option.dataset.factor) : 1;
                priceInput.value = String(+(parseFloat(meta.price) * factor).toFixed(6));
            }
            document.getElementById('lineRows').addEventListener('change', function (e) {
                var row = rowOf(e.target);
                if (!row) return;
                if (e.target.hasAttribute('data-itemsel')) {
                    filterUnits(row, true);
                    prefillPrice(row, false);
                    // Item default ҚҚС ставкаси (tax.md) - фақат бўш select'га
                    var meta = metaOf(row);
                    var taxSel = row.querySelector('select[data-taxsel]');
                    if (meta && taxSel && !taxSel.value && meta.ptax) taxSel.value = meta.ptax;
                } else if (e.target.hasAttribute('data-unitsel')) {
                    prefillPrice(row, true);
                }
            });
            document.querySelectorAll('#lineRows tr.line').forEach(function (row) {
                filterUnits(row, false);
            });
            document.body.addEventListener('htmx:afterSwap', function () {
                document.querySelectorAll('#lineRows tr.line').forEach(function (row) {
                    filterUnits(row, false);
                });
            });
        })();

        // Жонли Оралиқ жами (net) / ҚҚС / Жами (gross) - tax.md механизми
        // айнан (billForm кўзгуси). Фақат кўрсатиш.
        (function () {
            var body = document.getElementById('lineRows');
            var subEl = document.getElementById('poSubtotalLive');
            var taxEl = document.getElementById('poTaxLive');
            var totalEl = document.getElementById('poTotalLive');
            var modeSel = document.getElementById('poAmountsMode');
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

        <%-- DEC-097: курс prefill + кўриниш/home жами + ориентация -
             rate-block.js компонентида. Иккала эски IIFE ЎЧИРИЛДИ. --%>
</script>
`, actions = @`
    <a href="/purchase-orders" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="poForm">
            ${msg.localize("po.form.save")}</button>
    </div>
`)
