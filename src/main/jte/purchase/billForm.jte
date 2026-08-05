@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.purchase.web.BillForm
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param BillForm form
@param List<Contact> vendors
@param List<Currency> currencies
@param List<Item> items
@param List<Unit> units
@param List<Warehouse> warehouses
@param List<Account> expenseAccounts
@param List<TaxRate> taxRates
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<com.averpo.erp.shared.service.TxnClassService.ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("bill.form.title"), closeHref = "/bills", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="billForm" method="post" action="/bills">
        @template.shared.csrf(csrf = csrf)
        @if(form.getId() != null && !form.getId().isBlank())
            <input type="hidden" name="id" value="${form.getId()}">
        @endif
        <%-- Айлантириш оқими (estimates-po.md): сақлангач манба буюртма
             CLOSED + linked бўлади (BillController) --%>
        @if(form.getPurchaseOrderId() != null && !form.getPurchaseOrderId().isBlank())
            <input type="hidden" name="purchaseOrderId" value="${form.getPurchaseOrderId()}">
        @endif
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-5">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("bill.form.vendor")}
                <%-- Контакт валютаси value input data-currency'сида (087 занжири) --%>
                !{var selVendor = vendors.stream().filter(v -> v.getId().toString().equals(form.getVendorId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "vendorId", msg = msg,
                        value = form.getVendorId(),
                        placeholder = msg.lookup("bill.form.selectVendor"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/vendors/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addVendor") : null,
                        currencyTarget = "billCurrency",
                        homeCurrency = homeCurrency,
                        dataCurrency = selVendor != null ? (selVendor.getCurrency() != null ? selVendor.getCurrency().getCode() : homeCurrency) : null,
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("bill.form.selectVendor"))
                            @for(Contact vendor : vendors)
                                @template.shared.comboOption(value = vendor.getId().toString(),
                                        label = vendor.getDisplayName(),
                                        selected = vendor.getId().toString().equals(form.getVendorId()),
                                        dataCurrency = vendor.getCurrency() != null ? vendor.getCurrency().getCode() : homeCurrency)
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bill.form.vendorInvoice")}
                <input name="vendorInvoiceNumber" maxlength="100"
                       value="${form.getVendorInvoiceNumber() != null ? form.getVendorInvoiceNumber() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bill.form.date")}
                <input type="date" name="billDate" required
                       value="${form.getBillDate() != null ? form.getBillDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bill.form.dueDate")}
                <input type="date" name="dueDate"
                       value="${form.getDueDate() != null ? form.getDueDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <small class="block text-current/60">${msg.localize("bill.form.dueDateNote")}</small>
            </label>
            <%-- DEC-097: валюта select + курс майдони - shared.rateBlock
                 компоненти (prefill + кўриниш + кучли-валюта ориентация). --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "billDate", labelKey = "bill.form.currency",
                    rateKey = "bill.form.rate", currencyId = "billCurrency", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <%-- ҚҚС нарх режими (docs/modules/tax.md, QBO «Amounts are») --%>
                <span class="inline-flex items-center gap-1">${msg.localize("tax.amountsMode")} @template.shared.help(key = "field.vatMode", msg = msg)</span>
                <select name="amountsInclusive" id="billAmountsMode"
                        class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="false" selected="${!form.isAmountsInclusive()}">${msg.localize("tax.mode.exclusive")}</option>
                    <option value="true" selected="${form.isAmountsInclusive()}">${msg.localize("tax.mode.inclusive")}</option>
                </select>
            </label>
            @if("PER_TXN".equals(classMode))
                <%-- PER_TXN (class-tracking.md): битта Йўналиш - сатрларга тарқатилади --%>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("cls.label")}
                    @template.shared.classSelect(classes = classes, msg = msg,
                            name = "classId", selected = form.getClassId(),
                            cls = "mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark")
                </label>
            @endif
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("bill.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        @template.shared.table(cls = "min-w-[1000px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th>${msg.localize("bill.row.type")}</th>
                <th class="min-w-[180px]">${msg.localize("bill.row.itemOrAccount")}</th>
                <th>${msg.localize("bill.row.warehouse")}</th>
                <th>${msg.localize("bill.row.qty")}</th>
                <th>${msg.localize("bill.row.unit")}</th>
                <%-- U2: пул устунлари ёнида ҳужжат валютаси - танлов
                     ўзгарганда пастдаги script янгилаб туради --%>
                <th>${msg.localize("bill.row.price")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
                <th>${msg.localize("bill.row.amount")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
                <th>${msg.localize("tax.col.tax")}</th>
                @if("PER_LINE".equals(classMode))
                    <th>${msg.localize("cls.label")}</th>
                @endif
                <th>${msg.localize("bill.row.memo")}</th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="lineRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                @template.purchase.billLineRow(index = i, items = items,
                        units = units,
                        warehouses = warehouses, expenseAccounts = expenseAccounts,
                        taxRates = taxRates,
                        msg = msg, line = form.getLines().get(i),
                        classMode = classMode, classes = classes, canEdit = canEdit)
            @endfor
            </tbody>
        `)

        <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark mb-4"
                hx-get="/bills/line-row"
                hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr")].map(r => 1 + Number(r.dataset.index || 0)))}'
                hx-target="#lineRows" hx-swap="beforeend">${msg.localize("bill.form.addRow")}</button>

        <%-- DEC-088 1-банд: жамилар жадвал скролл ўрамидан ТАШҚАРИДА -
             кенг жадвалда қийматлар кесилмайди, доим кўз олдида (QBO Total
             қолипи; expenseForm намунаси). BA-004 recompute скрипти
             id бўйича ёзади - id'лар айнан сақланган, скрипт ўзгармаган. --%>
        <div style="display:flex;justify-content:flex-end;margin-bottom:1rem;">
            <div class="min-w-[260px] rounded-radius border border-outline bg-surface-alt px-4 py-3 dark:border-outline-dark dark:bg-surface-dark-alt">
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.subtotal")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="billSubtotalLive">0.00</span></span>
                </div>
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.col.tax")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="billTaxLive">0.00</span></span>
                </div>
                <div class="mt-1.5 flex justify-between gap-6 border-t border-outline pt-1.5 font-semibold dark:border-outline-dark">
                    <span>${msg.localize("tax.total")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="billTotalLive" data-rate-total>0.00</span>
                        <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></span>
                </div>
                <%-- DEC-088 4-банд: home жамиси (QBO Total жуфти) - жами × курс,
                     фақат кўрсатиш (server ҳисоби/penny rounding ўз жойида).
                     hidden эмас, display орқали: inline flex hidden'ни енгарди. --%>
                <div id="billTotalHomeRow" data-rate-home-row style="display:${form.getCurrency() != null && !form.getCurrency().equals(homeCurrency) ? "flex" : "none"}" class="justify-between gap-6 text-sm text-current/60">
                    <span>${msg.localize("tax.totalHome", homeCurrency)}</span>
                    <span id="billTotalHomeLive" data-rate-home style="white-space:nowrap;font-variant-numeric:tabular-nums;">0.00</span>
                </div>
            </div>
        </div>

        <p class="text-sm text-current/60">
            ${msg.localize("bill.form.homeNote", homeCurrency)}
        </p>
    </form>

    <script>
        // UoM (docs/modules/uom.md): item танланганда бирлик select'ида
        // фақат ўша item base гуруҳидаги бирликлар қолади, default харид
        // бирлиги танланади ва нарх prefill = каталог нарх × factor.
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
            // itemId энди combobox value input'ида (123 свипи)
            function metaOf(row) {
                var itemSel = row.querySelector('input[name$=".itemId"]');
                return (itemSel && META[itemSel.value]) || null;
            }
            // Бирлик рўйхатини item гуруҳига қараб фильтрлайди
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
            // Нарх prefill: каталог харид нархи (base'га) × танланган factor
            function prefillPrice(row, overwrite) {
                var meta = metaOf(row);
                var priceInput = row.querySelector('input[name$=".unitPrice"]');
                if (!meta || !meta.price || !priceInput) return;
                if (!overwrite && priceInput.value.trim() !== '') return;
                // IFRS-014: каталог харид нархи HOME валютада - ҳужжат
                // валютаси чет бўлса prefill қилинмайди (×курс жим хато
                // бўларди), майдон бўш қолади - фойдаланувчи ўзи теради
                var currency = ((document.getElementById('billCurrency') || {}).value || '').trim().toUpperCase();
                if (currency && currency !== "${homeCurrency}") return;
                var unitSel = row.querySelector('select[data-unitsel]');
                var option = unitSel && unitSel.options[unitSel.selectedIndex];
                var factor = option && option.value ? parseFloat(option.dataset.factor) : 1;
                var price = parseFloat(meta.price) * factor;
                priceInput.value = String(+price.toFixed(6));
            }
            document.getElementById('lineRows').addEventListener('change', function (e) {
                var row = rowOf(e.target);
                if (!row) return;
                if (e.target.name && e.target.name.endsWith('.itemId')) {
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
            // Мавжуд сатрлар (таҳрир формаси): танловга тегмасдан фильтр
            document.querySelectorAll('#lineRows tr.line').forEach(function (row) {
                filterUnits(row, false);
            });
            // HTMX билан қўшилган янги сатрлар ҳам дарҳол фильтрланади
            document.body.addEventListener('htmx:afterSwap', function () {
                document.querySelectorAll('#lineRows tr.line').forEach(function (row) {
                    filterUnits(row, false);
                });
            });
        })();

        // BA-004: сатр суммаси (миқдор × нарх) ва жами жонли
        // ҳисобланади - фақат кўрсатиш, server ҳисоби/BR'лар ўз жойида.
        // Формат Fmt.money кўзгуси: 2 хона, нуқта каср, NBSP минг ажратгич.
        (function () {
            var body = document.getElementById('lineRows');
            var totalEl = document.getElementById('billTotalLive');
            var subEl = document.getElementById('billSubtotalLive');
            var taxEl = document.getElementById('billTaxLive');
            var modeSel = document.getElementById('billAmountsMode');
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
                    var typeSel = row.querySelector('select[name$=".type"]');
                    var raw;
                    if (!typeSel || typeSel.value === 'ITEM') {
                        var qty = num((row.querySelector('input[name$=".quantity"]') || {}).value);
                        var price = num((row.querySelector('input[name$=".unitPrice"]') || {}).value);
                        raw = qty * price;
                        var span = row.querySelector('[data-lineamount]');
                        if (span) span.textContent = qty && price ? fmt(raw) : '';
                    } else {
                        raw = num((row.querySelector('input[name$=".amount"]') || {}).value);
                    }
                    // ҚҚС бўлиниши (tax.md): exclusive'да net=raw, inclusive'да
                    // net=raw/(1+r); фақат кўрсатиш - server ҳисоби ўз жойида
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
            // Сатр ўчирилганда (✕ tr.remove) ҳам жами янгиланади
            body.addEventListener('click', function () { setTimeout(recompute, 0); });
            document.body.addEventListener('htmx:afterSwap', recompute);
            recompute();
        })();

        <%-- DEC-097: курс prefill + кўриниш/home жами + ориентация -
             rate-block.js компонентида. Иккала эски IIFE ЎЧИРИЛДИ. --%>
</script>
`, actions = @`
    <a href="/bills" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit" form="billForm" name="action" value="draft">${msg.localize("bill.form.saveDraft")}</button>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="billForm" name="action" value="post">
            ${msg.localize("bill.form.savePost")}</button>
    </div>
`)
