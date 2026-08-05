@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.sales.web.InvoiceForm
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param InvoiceForm form
@param List<Contact> customers
@param List<Currency> currencies
@param List<Item> items
@param List<Unit> units
@param List<Warehouse> warehouses
@param List<TaxRate> taxRates
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<com.averpo.erp.shared.service.TxnClassService.ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("sinv.form.title"), closeHref = "/invoices", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="invForm" method="post" action="/invoices">
        @template.shared.csrf(csrf = csrf)
        @if(form.getId() != null && !form.getId().isBlank())
            <input type="hidden" name="id" value="${form.getId()}">
        @endif
        <%-- Айлантириш оқими (estimates-po.md): сақлангач манба estimate
             CLOSED + linked бўлади (InvoiceController) --%>
        @if(form.getEstimateId() != null && !form.getEstimateId().isBlank())
            <input type="hidden" name="estimateId" value="${form.getEstimateId()}">
        @endif
        <%-- DEC-134: header қатъий гридда (flex-wrap тартибсиз синарди:
             Rate лабели вертикал ҳарф бўлиб кетган, Memo узоқлашган).
             Схема: 1-қатор Мижоз(2 улуш)+Сана+Муддат; 2-қатор Валюта+
             Курс+Солиқ(+шартли Йўналиш); охирги қатор Memo тўлиқ кенглик.
             Тартиб DOM'дан - grid auto-flow ўзи жойлаштиради; мобилда
             1 устун стек, md'да 2 устун. --%>
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("sinv.form.customer")}
                <%-- Контакт валютаси value input data-currency'сида -
                     contact-currency.js занжири (087) ўша ерни ўқийди --%>
                !{var selCust = customers.stream().filter(c -> c.getId().toString().equals(form.getCustomerId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "customerId", msg = msg,
                        value = form.getCustomerId(),
                        placeholder = msg.lookup("sinv.form.selectCustomer"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/customers/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addCustomer") : null,
                        currencyTarget = "invCurrency",
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
                ${msg.localize("sinv.form.date")}
                <input type="date" name="invoiceDate" required
                       value="${form.getInvoiceDate() != null ? form.getInvoiceDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("sinv.form.dueDate")}
                <input type="date" name="dueDate"
                       value="${form.getDueDate() != null ? form.getDueDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <small class="block text-current/60">${msg.localize("sinv.form.dueDateNote")}</small>
            </label>
            <%-- DEC-097: валюта select + курс майдони - shared.rateBlock
                 компоненти (prefill + кўриниш + кучли-валюта ориентация). --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "invoiceDate", labelKey = "sinv.form.currency",
                    rateKey = "sinv.form.rate", currencyId = "invCurrency", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <%-- ҚҚС нарх режими (docs/modules/tax.md, QBO «Amounts are») --%>
                <span class="inline-flex items-center gap-1">${msg.localize("tax.amountsMode")} @template.shared.help(key = "field.vatMode", msg = msg)</span>
                <select name="amountsInclusive" id="invAmountsMode"
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
                ${msg.localize("sinv.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        @template.shared.table(cls = "min-w-[900px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[200px]">${msg.localize("sinv.row.item")}</th>
                <th>${msg.localize("sinv.row.warehouse")}</th>
                <th>${msg.localize("sinv.row.qty")}</th>
                <th>${msg.localize("sinv.row.unit")}</th>
                <%-- U2 паттерни: пул устуни ёнида ҳужжат валютаси --%>
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
                @template.sales.invoiceLineRow(index = i, items = items,
                        units = units,
                        warehouses = warehouses, taxRates = taxRates, msg = msg,
                        line = form.getLines().get(i),
                        classMode = classMode, classes = classes, canEdit = canEdit)
            @endfor
            </tbody>
        `)

        <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark mb-4"
                hx-get="/invoices/line-row"
                hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr")].map(r => 1 + Number(r.dataset.index || 0)))}'
                hx-target="#lineRows" hx-swap="beforeend">${msg.localize("sinv.form.addRow")}</button>

        <%-- DEC-088 1-банд: жамилар жадвал скролл ўрамидан ТАШҚАРИДА -
             кенг жадвалда қийматлар кесилмайди, доим кўз олдида (QBO Total
             қолипи; expenseForm намунаси). Recompute скрипти id бўйича ёзади -
             id'лар айнан сақланган, скрипт ўзгармаган. --%>
        <div style="display:flex;justify-content:flex-end;margin-bottom:1rem;">
            <div class="min-w-[260px] rounded-radius border border-outline bg-surface-alt px-4 py-3 dark:border-outline-dark dark:bg-surface-dark-alt">
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.subtotal")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="invSubtotalLive">0.00</span></span>
                </div>
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <span>${msg.localize("tax.col.tax")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="invTaxLive">0.00</span></span>
                </div>
                <div class="mt-1.5 flex justify-between gap-6 border-t border-outline pt-1.5 font-semibold dark:border-outline-dark">
                    <span>${msg.localize("tax.total")}</span>
                    <span style="white-space:nowrap;font-variant-numeric:tabular-nums;"><span id="invTotalLive" data-rate-total>0.00</span>
                        <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></span>
                </div>
                <%-- DEC-088/097: home жамиси - жами × КАНОНИК курс; кўрсат/
                     яшир ва ҳисоб rate-block.js'да (data-rate-*). --%>
                <div id="invTotalHomeRow" data-rate-home-row style="display:${form.getCurrency() != null && !form.getCurrency().equals(homeCurrency) ? "flex" : "none"}" class="justify-between gap-6 text-sm text-current/60">
                    <span>${msg.localize("tax.totalHome", homeCurrency)}</span>
                    <span id="invTotalHomeLive" data-rate-home style="white-space:nowrap;font-variant-numeric:tabular-nums;">0.00</span>
                </div>
            </div>
        </div>

        <p class="text-sm text-current/60">
            ${msg.localize("sinv.form.homeNote", homeCurrency)}
        </p>
    </form>

    <script>
        // UoM (docs/modules/uom.md): item танланганда бирлик select'ида
        // фақат ўша item base гуруҳидаги бирликлар қолади, default сотув
        // бирлиги танланади ва нарх prefill = каталог нарх × factor
        // (billForm скриптининг кўзгуси).
        (function () {
            var META = {
                @for(Item item : items)
                    "${item.getId().toString()}": {
                        base: "${item.getUnit() == null ? "" : item.getUnit().getId().toString()}",
                        group: "${item.getUnit() == null || item.getUnit().getGroup() == null ? "" : item.getUnit().getGroup().getId().toString()}",
                        def: "${item.getSalesUnit() != null ? item.getSalesUnit().getId().toString() : (item.getUnit() == null ? "" : item.getUnit().getId().toString())}",
                        price: "${item.getSalesPrice() == null ? "" : item.getSalesPrice().stripTrailingZeros().toPlainString()}",
                        stax: "${item.getSalesTaxRateId() == null ? "" : item.getSalesTaxRateId().toString()}"
                    },
                @endfor
            };
            function rowOf(el) { return el.closest('tr.line'); }
            // data-itemsel энди combobox value input'ида (123 свипи)
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
            // Нарх prefill (docs/modules/price-list.md 3-туртки): аввал
            // нарх рўйхатидан (мижоз/миқдор/санага мос per-base-unit
            // нарх), топилмаса каталог нархидан - иккови ҳам × танланган
            // бирлик factor. data-autofill белгиси қўлда терилган нархни
            // сақлайди: item/бирлик алмашса устига ёзилади (force), миқдор/
            // мижоз алмашса фақат автоматик тўлдирилган қиймат янгиланади.
            function factorOf(row) {
                var unitSel = row.querySelector('select[data-unitsel]');
                var option = unitSel && unitSel.options[unitSel.selectedIndex];
                return option && option.value ? parseFloat(option.dataset.factor) : 1;
            }
            function applyPrice(input, value) {
                input.value = String(+Number(value).toFixed(6));
                input.dataset.autofill = '1';
                // Программ нарх ўзгариши ҚҚС/жами жонли ҳисобини янгиласин
                // (tax.md IIFE'си change'ни тинглайди). change'ни отамиз,
                // input'ни эмас: input-listener'имиз autofill'ни ўчирар эди.
                input.dispatchEvent(new Event('change', { bubbles: true }));
            }
            function isManual(input) {
                return input.value.trim() !== '' && input.dataset.autofill !== '1';
            }
            function prefillPrice(row, force) {
                var priceInput = row.querySelector('input[name$=".unitPrice"]');
                var itemSel = row.querySelector('[data-itemsel]');
                if (!priceInput || !itemSel || !itemSel.value) return;
                if (isManual(priceInput) && !force) return;
                var meta = metaOf(row);
                var factor = factorOf(row);
                var qtyInput = row.querySelector('input[name$=".quantity"]');
                var qtyText = qtyInput ? qtyInput.value.trim().replace(',', '.') : '';
                var qty = qtyText !== '' ? parseFloat(qtyText) : 1;
                if (!(qty > 0)) qty = 1;
                var baseQty = qty * factor;
                var customerId = (document.querySelector('input[name="customerId"]') || {}).value || '';
                var currency = (document.getElementById('invCurrency') || {}).value || '';
                var date = (document.querySelector('input[name="invoiceDate"]') || {}).value || '';
                var url = '/price-lists/lookup?itemId=' + encodeURIComponent(itemSel.value)
                        + '&qty=' + encodeURIComponent(baseQty)
                        + '&currency=' + encodeURIComponent(currency)
                        + '&date=' + encodeURIComponent(date)
                        + (customerId ? '&customerId=' + encodeURIComponent(customerId) : '');
                fetch(url).then(function (r) { return r.ok ? r.text() : ''; })
                    .then(function (listPrice) {
                        // Ора вақтда қўлда терилган бўлса устига ёзмаймиз
                        if (isManual(priceInput) && !force) return;
                        if (listPrice && listPrice.trim() !== '') {
                            applyPrice(priceInput, parseFloat(listPrice) * factor);
                        } else if (meta && meta.price && (!currency || currency.toUpperCase() === "${homeCurrency}")) {
                            // IFRS-014: каталог нархи HOME валютада - чет
                            // валютали ҳужжатга қуйилмайди (×курс жим хато
                            // бўларди); пастдаги тармоқ эскирган autofill'ни
                            // тозалайди, майдон бўш қолади
                            applyPrice(priceInput, parseFloat(meta.price) * factor);
                        } else if (priceInput.dataset.autofill === '1') {
                            // Мос нарх йўқ, аммо қиймат автоматик эди (масалан
                            // поғона миқдоридан пастга тушди, каталог нархи ҳам
                            // йўқ) - эски таклиф эскириб қолмасин, тозаланади
                            priceInput.value = '';
                            priceInput.dataset.autofill = '';
                            // Тозалангач ҳам ҚҚС/жами қайта ҳисоблансин
                            priceInput.dispatchEvent(new Event('change', { bubbles: true }));
                        }
                    })
                    .catch(function () { /* prefill - тармоқ хатоси жим ўтади */ });
            }
            document.getElementById('lineRows').addEventListener('change', function (e) {
                var row = rowOf(e.target);
                if (!row) return;
                if (e.target.hasAttribute('data-itemsel')) {
                    filterUnits(row, true);
                    prefillPrice(row, true);    // item алмашди - янги item нархи
                    // Item default ҚҚС ставкаси (tax.md) - фақат бўш select'га
                    var meta = metaOf(row);
                    var taxSel = row.querySelector('select[data-taxsel]');
                    if (meta && taxSel && !taxSel.value && meta.stax) taxSel.value = meta.stax;
                } else if (e.target.hasAttribute('data-unitsel')) {
                    prefillPrice(row, true);    // бирлик - нарх қайта масштабланади
                } else if (e.target.name && e.target.name.slice(-9) === '.quantity') {
                    prefillPrice(row, false);   // миқдор - поғона (қўлда нарх сақланади)
                }
            });
            // Нарх майдони қўлда таҳрирланса autofill белгиси кетади
            document.getElementById('lineRows').addEventListener('input', function (e) {
                if (e.target.name && e.target.name.slice(-10) === '.unitPrice') {
                    e.target.dataset.autofill = '';
                }
            });
            // Мижоз алмашса барча сатр поғонаси қайта ечилади (default→
            // мижоз рўйхати); қўлда терилган нархлар тегилмайди
            var custSel = document.querySelector('input[name="customerId"]');
            if (custSel) custSel.addEventListener('change', function () {
                document.querySelectorAll('#lineRows tr.line').forEach(function (row) {
                    prefillPrice(row, false);
                });
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

        // Жонли Оралиқ жами (net) / ҚҚС / Жами (gross) - docs/modules/tax.md
        // (billForm кўзгуси). Фақат кўрсатиш; server ҳисоби ўз жойида.
        (function () {
            var body = document.getElementById('lineRows');
            var subEl = document.getElementById('invSubtotalLive');
            var taxEl = document.getElementById('invTaxLive');
            var totalEl = document.getElementById('invTotalLive');
            var modeSel = document.getElementById('invAmountsMode');
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

        <%-- DEC-097: курс prefill (DEC-029) + кўриниш/home жами
             (DEC-088) + кучли-валюта ориентация - rate-block.js
             компонентида. Иккала эски IIFE шу ерда ЎЧИРИЛДИ. --%>
    </script>
`, actions = @`
    <a href="/invoices" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit" form="invForm" name="action" value="draft">${msg.localize("sinv.form.saveDraft")}</button>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="invForm" name="action" value="post">
            ${msg.localize("sinv.form.savePost")}</button>
    </div>
`)
