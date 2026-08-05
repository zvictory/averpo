@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.ItemCategory
@import com.averpo.erp.item.domain.ItemType
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.item.service.ItemService
@import com.averpo.erp.item.web.ItemForm
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@import java.util.Map
@param ItemForm form
@param List<ItemCategory> categories
@param List<Unit> units
@param List<Account> incomeAccounts
@param List<Account> expenseAccounts
@param List<Account> assetAccounts
@param List<TaxRate> taxRates
@param Map<String, ItemService.DefaultAccounts> defaultsByType
@param String homeCurrency
@param Msg msg
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String editId = null
@param String error = null
@param Boolean drawer = false
@param Boolean canEdit = false

<%-- DEC-024: форма таначаси - тўлиқ саҳифа (form.jte) ва drawer
     (formDrawer.jte) иккиси ҳам шу partial'ни ишлатади. drawer=true
     бўлса форма HTMX орқали submit бўлади (#drawerBody'га қайта render
     ёки HX-Redirect); false'да оддий POST - JS'сиз fallback шарт. --%>
    @template.shared.alert(error = error)

    <script>
        <%-- Тип ўзгарганда default счётлар (фақат яратишда ишлатилади) --%>
        window.itemDefaults = {
            @for(Map.Entry<String, ItemService.DefaultAccounts> entry : defaultsByType.entrySet())
                "${entry.getKey()}": {
                    income: "${entry.getValue().income() == null ? "" : entry.getValue().income().toString()}",
                    expense: "${entry.getValue().expense() == null ? "" : entry.getValue().expense().toString()}",
                    asset: "${entry.getValue().inventoryAsset() == null ? "" : entry.getValue().inventoryAsset().toString()}"
                },
            @endfor
        };
        window.applyItemDefaults = function (type) {
            var d = window.itemDefaults[type] || {};
            // Счёт майдонлари combobox (native select ЭМАС): расмий ташқи
            // ёзув API - input[name].value + change dispatch (penguin-
            // combobox.js:17). change тингловчиси ($refs.value) combobox
            // дисплейини ЎЗИ синхронлайди. Майдон топилмаса жим ўтади
            function setCombo(name, val) {
                var el = document.querySelector('input[name=' + name + ']');
                if (!el) return;
                el.value = val || '';
                el.dispatchEvent(new Event('change', {bubbles: true}));
            }
            setCombo('incomeAccountId', d.income);
            setCombo('expenseAccountId', d.expense);
            setCombo('inventoryAssetAccountId', d.asset);
            window.filterExpenseAccounts(type);
        };
        <%-- QBO: харажат счёти combobox'и item типига қараб филтрланади -
             INVENTORY→COGS (COGSAccountRef), SERVICE/NON_INVENTORY→EXPENSE
             (ExpenseAccountRef). Ташқи филтр li.hidden ишлатади (penguin-
             combobox.js). Тури мос келмаган гуруҳ сарлавҳаси ҳам яширилади;
             айни танланган счёт ҳеч қачон яширилмайди (эски танловни узмаслик) --%>
        window.filterExpenseAccounts = function (type) {
            var hidden = document.querySelector('input[name=expenseAccountId]');
            if (!hidden) return;
            var root = hidden.closest('[data-combobox]');
            if (!root) return;
            var list = root.querySelector('[role=listbox]');
            if (!list) return;
            var wantCogs = type === 'INVENTORY';
            var current = hidden.value;
            var header = null, headerVisible = false;
            for (var i = 0; i < list.children.length; i++) {
                var el = list.children[i];
                var role = el.getAttribute('role');
                if (role === 'presentation') {
                    if (header) header.hidden = !headerVisible;
                    header = el; headerVisible = false;
                } else if (role === 'option' && el.dataset.value) {
                    var t = el.dataset.acctype || '';
                    var ok = (wantCogs ? t === 'COST_OF_GOODS_SOLD' : t === 'EXPENSE')
                            || el.dataset.value === current;
                    el.hidden = !ok;
                    if (ok) headerVisible = true;
                }
            }
            if (header) header.hidden = !headerVisible;
        };
        <%-- UoM (BR-ITM-012): default бирлик select'ларида фақат base
             гуруҳидаги бирликлар қолади - бирлик id → гуруҳ id луғати --%>
        window.unitGroups = {
            @for(Unit unit : units)
                "${unit.getId().toString()}": "${unit.getGroup() == null ? "" : unit.getGroup().getId().toString()}",
            @endfor
        };
        window.filterUnitDefaults = function () {
            var baseId = document.querySelector('select[name=unitId]').value;
            var group = baseId ? (window.unitGroups[baseId] || '') : '';
            ['purchaseUnitId', 'salesUnitId'].forEach(function (name) {
                var select = document.querySelector('select[name=' + name + ']');
                for (var i = 0; i < select.options.length; i++) {
                    var option = select.options[i];
                    if (!option.value) continue;
                    var ok = group !== '' && window.unitGroups[option.value] === group;
                    option.hidden = !ok;
                    option.disabled = !ok;
                }
                var current = select.options[select.selectedIndex];
                if (current && current.disabled) select.value = '';
            });
        };
        // Drawer'да DOMContentLoaded аллақачон ўтган (HTMX swap) - дарҳол
        // ишга тушади; тўлиқ саҳифада эса аввалгидек event кутилади
        function initItemForm() {
            window.filterUnitDefaults();
            var typeEl = document.querySelector('[name=type]');
            if (typeEl) window.filterExpenseAccounts(typeEl.value);
        }
        if (document.readyState !== 'loading') initItemForm();
        else document.addEventListener('DOMContentLoaded', initItemForm);
    </script>

    <form id="itemForm" method="post" action="${editId == null ? "/items" : "/items/" + editId}"
          hx-post="${drawer != null && drawer ? (editId == null ? "/items" : "/items/" + editId) : null}"
          hx-target="${drawer != null && drawer ? "#drawerBody" : null}"
          hx-swap="${drawer != null && drawer ? "innerHTML" : null}"
          x-data="{dt: '${form.getType()}'}"
          class="${drawer != null && drawer ? "grid gap-[.9rem]" : "grid max-w-[720px] gap-[.9rem] rounded-radius border border-outline bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark"}">
        <%-- DEC-053 банд 6: майдон гридлари 375px да бир устунга йиғилади --%>
        @template.shared.csrf(csrf = csrf)

        @if(editId == null)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.type")}
                <select name="type" x-model="dt" required
                        x-on:change="window.applyItemDefaults($event.target.value)"
                        class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    @for(ItemType type : ItemType.values())
                        <option value="${type.name()}" selected="${type.name().equals(form.getType())}">
                            ${msg.localize("item.type." + type.name())}
                        </option>
                    @endfor
                </select>
            </label>
        @else
            <input type="hidden" name="type" value="${form.getType()}">
            <p class="text-sm text-current/60">${msg.localize("item.form.type")}:
                <b>${msg.localize("item.type." + form.getType())}</b></p>
        @endif

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("item.form.name")}
            <input name="name" value="${form.getName()}" required
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>

        <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-3">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.sku")}
                <input name="sku" value="${form.getSku()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.category")}
                @template.shared.combobox(name = "categoryId", msg = msg,
                        value = form.getCategoryId(),
                        placeholder = msg.lookup("accounts.form.parentNone"),
                        cls = "mt-1 w-full",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("accounts.form.parentNone"))
                            @for(ItemCategory category : categories)
                                @template.shared.comboOption(value = category.getId().toString(),
                                        label = category.getName(),
                                        selected = category.getId().toString().equals(form.getCategoryId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.unit")}
                <%-- Бирлик - native select (сурув: filterUnitDefaults
                     option'ларни программ бошқаради) --%>
                <select name="unitId" onchange="window.filterUnitDefaults()" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"
                       >
                    <option value="">${msg.localize("accounts.form.parentNone")}</option>
                    @for(Unit unit : units)
                        <option value="${unit.getId().toString()}"
                                selected="${unit.getId().toString().equals(form.getUnitId())}">
                            ${unit.getName()}
                        </option>
                    @endfor
                </select>
            </label>
        </div>

        <%-- Сотув бўлими (QBO Sales section) --%>
        <div class="grid gap-3.5 border-t border-outline pt-3.5 dark:border-outline-dark">
            <b>${msg.localize("item.form.salesSection")}</b>
            <%-- 2 устунли (QBO нақши): нарх|бирлик ёнма-ён, счёт ва ҚҚС
                 ҳар бири ўз қаторида - счёт номи узун, сиқилмаслиги учун --%>
            <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-2">
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <%-- U5: нарх home валютада киритилади --%>
                    <span>${msg.localize("item.form.salesPrice")} <span class="text-current/60">${homeCurrency}</span></span>
                    <input name="salesPrice" value="${form.getSalesPrice()}" class="money mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                          >
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("item.form.salesUnit")}
                    <select name="salesUnitId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        <option value="">${msg.localize("accounts.form.parentNone")}</option>
                        @for(Unit unit : units)
                            <option value="${unit.getId().toString()}"
                                    selected="${unit.getId().toString().equals(form.getSalesUnitId())}">
                                ${unit.getName()}
                            </option>
                        @endfor
                    </select>
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-[521px]:col-span-2">
                    ${msg.localize("item.form.incomeAccount")}
                    <%-- DEC-014: QBO tree --%>
                    @template.shared.combobox(name = "incomeAccountId", msg = msg,
                            value = form.getIncomeAccountId(),
                            placeholder = msg.lookup("accounts.form.select"),
                            required = true,
                            cls = "mt-1 w-full",
                            addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                            addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                            options = @`
                                @template.shared.comboOption(value = "", label = msg.lookup("accounts.form.select"))
                                @template.shared.comboAccountOptions(accounts = incomeAccounts, msg = msg,
                                        selected = form.getIncomeAccountId())
                            `)
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <%-- Default сотув ҚҚСи (docs/modules/tax.md) - invoice сатрида prefill --%>
                    ${msg.localize("tax.title")}
                    <select name="salesTaxRateId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        <option value="">${msg.localize("tax.none")}</option>
                        @for(TaxRate rate : taxRates)
                            <option value="${rate.getId().toString()}"
                                    selected="${rate.getId().toString().equals(form.getSalesTaxRateId())}">
                                ${rate.getName()}
                            </option>
                        @endfor
                    </select>
                </label>
            </div>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.salesDescription")}
                <input name="salesDescription" value="${form.getSalesDescription()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <%-- Харид бўлими (QBO Purchasing section) --%>
        <div class="grid gap-3.5 border-t border-outline pt-3.5 dark:border-outline-dark">
            <b>${msg.localize("item.form.purchaseSection")}</b>
            <%-- 2 устунли (QBO нақши): нарх|бирлик ёнма-ён, харажат счёти ва
                 ҚҚС ҳар бири ўз қаторида - счёт номи узун, сиқилмаслиги учун --%>
            <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-2">
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <span>${msg.localize("item.form.purchaseCost")} <span class="text-current/60">${homeCurrency}</span></span>
                    <input name="purchaseCost" value="${form.getPurchaseCost()}" class="money mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                          >
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("item.form.purchaseUnit")}
                    <select name="purchaseUnitId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        <option value="">${msg.localize("accounts.form.parentNone")}</option>
                        @for(Unit unit : units)
                            <option value="${unit.getId().toString()}"
                                    selected="${unit.getId().toString().equals(form.getPurchaseUnitId())}">
                                ${unit.getName()}
                            </option>
                        @endfor
                    </select>
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-[521px]:col-span-2">
                    ${msg.localize("item.form.expenseAccount")}
                    <%-- DEC-014: QBO tree --%>
                    @template.shared.combobox(name = "expenseAccountId", msg = msg,
                            value = form.getExpenseAccountId(),
                            placeholder = msg.lookup("accounts.form.select"),
                            required = true,
                            cls = "mt-1 w-full",
                            addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                            addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                            options = @`
                                @template.shared.comboOption(value = "", label = msg.lookup("accounts.form.select"))
                                @template.shared.comboAccountOptions(accounts = expenseAccounts, msg = msg,
                                        selected = form.getExpenseAccountId())
                            `)
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <%-- Default харид ҚҚСи (docs/modules/tax.md) - bill сатрида prefill --%>
                    ${msg.localize("tax.title")}
                    <select name="purchaseTaxRateId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        <option value="">${msg.localize("tax.none")}</option>
                        @for(TaxRate rate : taxRates)
                            <option value="${rate.getId().toString()}"
                                    selected="${rate.getId().toString().equals(form.getPurchaseTaxRateId())}">
                                ${rate.getName()}
                            </option>
                        @endfor
                    </select>
                </label>
            </div>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.purchaseDescription")}
                <input name="purchaseDescription" value="${form.getPurchaseDescription()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <%-- Омбор бўлими - фақат INVENTORY типда (Alpine toggle) --%>
        <div class="grid gap-3.5 border-t border-outline pt-3.5 dark:border-outline-dark" x-show="dt === 'INVENTORY'">
            <b>${msg.localize("item.form.inventorySection")}</b>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("item.form.assetAccount")}
                <%-- DEC-014: QBO tree --%>
                @template.shared.combobox(name = "inventoryAssetAccountId", msg = msg,
                        value = form.getInventoryAssetAccountId(),
                        placeholder = msg.lookup("accounts.form.select"),
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("accounts.form.select"))
                            @template.shared.comboAccountOptions(accounts = assetAccounts, msg = msg,
                                    selected = form.getInventoryAssetAccountId())
                        `)
            </label>
        </div>

        @if(editId != null)
            <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-2">
                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${form.isActive()}">
                <input type="hidden" name="_active" value="on">
                ${msg.localize("accounts.form.active")}
            </label>
        @endif
    </form>
