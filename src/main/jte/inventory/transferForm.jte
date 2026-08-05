@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.inventory.web.TransferForm
@import com.averpo.erp.item.domain.Item
@import java.util.List
@param TransferForm form
@param List<Item> items
@param List<Warehouse> warehouses
@param String today
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null

<%-- Ҳужжатли омборлараро кўчириш акти формаси (DEC-093): манба/манзил
     омбор, кўп сатр (item + qty). Дарҳол POSTED, GL'сиз (posting-rules). --%>
@template.layout.form(title = msg.lookup("inv.transfer.formTitle"), closeHref = "/inventory/transfers", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="wtrForm" method="post" action="/inventory/transfers">
        @template.shared.csrf(csrf = csrf)
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("inv.transfer.from")}
                <%-- id wtrFromWarehouse - манба on-hand юклаш скрипти учун --%>
                @template.shared.combobox(name = "fromWarehouseId", msg = msg,
                        value = form.getFromWarehouseId(),
                        placeholder = "-",
                        required = true,
                        cls = "mt-1 w-full",
                        id = "wtrFromWarehouse",
                        options = @`
                            @template.shared.comboOption(value = "", label = "-")
                            @for(Warehouse warehouse : warehouses)
                                @template.shared.comboOption(value = warehouse.getId().toString(),
                                        label = warehouse.getName(),
                                        selected = warehouse.getId().toString().equals(form.getFromWarehouseId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("inv.transfer.to")}
                @template.shared.combobox(name = "toWarehouseId", msg = msg,
                        value = form.getToWarehouseId(),
                        placeholder = "-",
                        required = true,
                        cls = "mt-1 w-full",
                        options = @`
                            @template.shared.comboOption(value = "", label = "-")
                            @for(Warehouse warehouse : warehouses)
                                @template.shared.comboOption(value = warehouse.getId().toString(),
                                        label = warehouse.getName(),
                                        selected = warehouse.getId().toString().equals(form.getToWarehouseId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("inv.col.date")}
                <input type="date" name="date" required
                       value="${form.getDate() != null ? form.getDate().toString() : today}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <%-- DEC-109: ташқи (қоғоз) ҳужжат рақами - QBO Reference no. --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("inv.col.externalRef")}
                <input name="externalRef" maxlength="50"
                       value="${form.getExternalRef() != null ? form.getExternalRef() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("inv.col.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        @template.shared.table(cls = "min-w-[640px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[220px]">${msg.localize("inv.col.item")}</th>
                <%-- DEC-109: манба омбордаги қолдиқ (бирлик билан) --%>
                <th class="text-right">${msg.localize("inv.transfer.available")}</th>
                <th class="text-right">${msg.localize("inv.col.qty")}</th>
                <th>${msg.localize("inv.col.memo")}</th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="wtrRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                <tr class="line" data-index="${i}">
                    <td class="rowno text-on-surface/50 dark:text-on-surface-dark/50">${i + 1}</td>
                    <td>
                        <%-- Бирлик номи value input data-unit'ида - қолдиқ
                             кўрсаткичи ўша ердан ўқилади. idx/rowLine -
                             lambda учун effectively-final нусхалар --%>
                        !{var idx = i;}
                        !{var rowLine = form.getLines().get(i);}
                        !{var selIt = items.stream().filter(it -> it.getId().toString().equals(rowLine.getItemId())).findFirst().orElse(null);}
                        @template.shared.combobox(name = "lines[" + idx + "].itemId", msg = msg,
                                value = rowLine.getItemId(),
                                placeholder = "-",
                                cls = "w-full",
                                dataUnit = selIt != null && selIt.getUnit() != null ? selIt.getUnit().getName() : null,
                                options = @`
                                    @template.shared.comboOption(value = "", label = "-")
                                    @for(Item item : items)
                                        @template.shared.comboOption(value = item.getId().toString(),
                                                label = item.getName(),
                                                selected = item.getId().toString().equals(rowLine.getItemId()),
                                                dataUnit = item.getUnit() != null ? item.getUnit().getName() : null)
                                    @endfor
                                `)
                    </td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong"><span class="wtr-current">-</span></td>
                    <td><input name="lines[${i}].qty" inputmode="decimal" class="wtr-qty w-28 text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"
                               value="${form.getLines().get(i).getQty() != null ? form.getLines().get(i).getQty() : ""}"
                              ></td>
                    <td><input name="lines[${i}].memo" maxlength="500"
                               value="${form.getLines().get(i).getMemo() != null ? form.getLines().get(i).getMemo() : ""}"
                               class="w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
                    <td class="text-center"><button type="button" class="cursor-pointer border-0 bg-transparent p-0 text-[1.1rem] text-danger wtr-remove" title="${msg.lookup("common.deleteLine")}">×</button></td>
                </tr>
            @endfor
            </tbody>
        `)
        <button type="button" id="wtrAddRow" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark mb-4">${msg.localize("bt.form.addRow")}</button>

        <p class="text-sm text-current/60">${msg.localize("inv.transfer.formNote")}</p>
    </form>

    <template id="wtrLineTpl">
        <tr class="line" data-index="__IDX__">
            <td class="rowno text-on-surface/50 dark:text-on-surface-dark/50"></td>
            <td>
                @template.shared.combobox(name = "lines[__IDX__].itemId", msg = msg,
                        placeholder = "-",
                        cls = "w-full",
                        options = @`
                            @template.shared.comboOption(value = "", label = "-")
                            @for(Item item : items)
                                @template.shared.comboOption(value = item.getId().toString(),
                                        label = item.getName(),
                                        dataUnit = item.getUnit() != null ? item.getUnit().getName() : null)
                            @endfor
                        `)
            </td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong"><span class="wtr-current">-</span></td>
            <td><input name="lines[__IDX__].qty" inputmode="decimal" class="wtr-qty w-28 text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
            <td><input name="lines[__IDX__].memo" maxlength="500" class="w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
            <td class="text-center"><button type="button" class="cursor-pointer border-0 bg-transparent p-0 text-[1.1rem] text-danger wtr-remove">×</button></td>
        </tr>
    </template>
`, actions = @`
    <a href="/inventory/transfers" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="wtrForm">${msg.localize("common.save")}</button>
    </div>
`)

<script>
    // Кўчириш формаси (DEC-109): манба омбор қолдиғи + бирлик ҳар
    // сатрда (adjustmentForm on-hand нақши, лекин МАНБА омборга кўра);
    // миқдор қолдиқдан ошса қизил ҳинт (server BR-INV-003 ҳақиқат манбаи);
    // сатр қўшиш/ўчириш (JS clone).
    (function () {
        var onhand = {};
        var fromSel = document.getElementById('wtrFromWarehouse');
        var body = document.getElementById('wtrRows');
        var tpl = document.getElementById('wtrLineTpl');
        var addBtn = document.getElementById('wtrAddRow');
        if (!body) return;

        function loadOnHand() {
            var id = fromSel ? fromSel.value : '';
            if (!id) { onhand = {}; refreshAll(); return; }
            fetch('/inventory/on-hand?warehouseId=' + encodeURIComponent(id))
                .then(function (r) { return r.ok ? r.json() : {}; })
                .then(function (map) { onhand = map || {}; refreshAll(); });
        }
        function num(v) {
            var raw = (v || '').replace(/\s/g, '').replace(',', '.');
            var n = parseFloat(raw);
            return isNaN(n) ? null : n;
        }
        function trim(n) {
            return String(n).replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
        }
        function refreshRow(row) {
            // itemId энди combobox value input'ида (123 свипи)
            var itemSel = row.querySelector('input[name$=".itemId"]');
            var curEl = row.querySelector('.wtr-current');
            var qtyInput = row.querySelector('.wtr-qty');
            if (!itemSel || !curEl) return;
            var id = itemSel.value;
            var unit = itemSel.dataset.unit || '';
            var current = id ? (onhand[id] != null ? parseFloat(onhand[id]) : 0) : null;
            curEl.textContent = current == null ? '-'
                    : (trim(current) + (unit ? ' ' + unit : ''));
            // Миқдор манба қолдиғидан ошса қизил ҳинт (server гарови барибир ушлайди)
            var nv = num(qtyInput.value);
            var over = current != null && nv != null && nv > current;
            qtyInput.style.color = over ? '#b91c1c' : '';
            qtyInput.style.borderColor = over ? '#b91c1c' : '';
        }
        function refreshAll() { body.querySelectorAll('tr.line').forEach(refreshRow); }
        function renumber() {
            var i = 0;
            body.querySelectorAll('tr.line').forEach(function (row) {
                row.dataset.index = i;
                var no = row.querySelector('.rowno');
                if (no) no.textContent = i + 1;
                i++;
            });
        }
        body.addEventListener('change', function (e) {
            if (e.target.name && e.target.name.endsWith('.itemId')) refreshRow(e.target.closest('tr.line'));
        });
        body.addEventListener('input', function (e) {
            if (e.target.classList.contains('wtr-qty')) refreshRow(e.target.closest('tr.line'));
        });
        body.addEventListener('click', function (e) {
            if (e.target.classList.contains('wtr-remove')) {
                var rows = body.querySelectorAll('tr.line');
                if (rows.length > 1) { e.target.closest('tr.line').remove(); renumber(); }
            }
        });
        if (fromSel) fromSel.addEventListener('change', loadOnHand);
        if (addBtn && tpl) {
            addBtn.addEventListener('click', function () {
                var idx = body.querySelectorAll('tr.line').length;
                var html = tpl.innerHTML.replace(/__IDX__/g, idx);
                var t = document.createElement('tbody');
                t.innerHTML = html.trim();
                body.appendChild(t.firstElementChild);
                renumber();
            });
        }
        loadOnHand();
    })();
</script>
