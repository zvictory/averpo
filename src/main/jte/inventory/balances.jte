@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.inventory.web.InventoryController.BalanceRow
@import com.averpo.erp.item.domain.ItemCategory
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<BalanceRow> rows
@param org.springframework.data.domain.Page<BalanceRow> page = null
@param String filterQuery = ""
@param List<Warehouse> warehouses
@param List<ItemCategory> categories = java.util.List.of()
@param String selectedWarehouseId
@param String selectedCategoryId = ""
@param String q = ""
@param Boolean hideZero = true
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.stockBalances", title = msg.lookup("inv.balances.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("inv.balances.title")}</h1>
        @if(canEdit != null && canEdit)
            <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
                <a href="/inventory/adjustments" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("inv.adjust.title")}</a>
                <a href="/inventory/transfers" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark">${msg.localize("inv.transfer.title")}</a>
            </div>
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Мукаммал филтр (DEC-093): омбор, item қидирув, категория,
         нолни яшир (default ON - QBO услуби). --%>
    <form method="get" action="/inventory/balances" style="margin-bottom:1rem;display:flex;gap:.75rem;align-items:end;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.warehouse")}
            @template.shared.combobox(name = "warehouseId", msg = msg,
                    value = selectedWarehouseId,
                    placeholder = msg.lookup("inv.allWarehouses"),
                    cls = "mt-1 min-w-[180px]",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("inv.allWarehouses"))
                        @for(Warehouse warehouse : warehouses)
                            @template.shared.comboOption(value = warehouse.getId().toString(),
                                    label = warehouse.getName(),
                                    selected = warehouse.getId().toString().equals(selectedWarehouseId))
                        @endfor
                    `)
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("inv.filter.itemSearch")}
            <input name="q" value="${q}" placeholder="${msg.lookup("inv.filter.itemSearchPh")}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("inv.filter.category")}
            @template.shared.combobox(name = "categoryId", msg = msg,
                    value = selectedCategoryId,
                    placeholder = msg.lookup("filter.all"),
                    cls = "mt-1 min-w-[160px]",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("filter.all"))
                        @for(ItemCategory category : categories)
                            @template.shared.comboOption(value = category.getId().toString(),
                                    label = category.getName(),
                                    selected = category.getId().toString().equals(selectedCategoryId))
                        @endfor
                    `)
        </label>
        <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5">
            <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="hideZero" value="true" checked="${hideZero != null && hideZero}">
            ${msg.localize("inv.filter.hideZero")}
        </label>
        <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("filter.apply")}</button>
    </form>

    @if(rows.isEmpty())
        <p class="text-current/60">${msg.localize("inv.empty")}</p>
    @else
            @template.shared.table(c = @`
                <thead>
                <tr>
                    <th>${msg.localize("inv.col.item")}</th>
                    <th>${msg.localize("inv.col.warehouse")}</th>
                    <th class="text-right">${msg.localize("inv.col.qty")}</th>
                    <th class="text-right">${msg.localize("inv.col.avgCost")} (${homeCurrency})</th>
                    <th class="text-right">${msg.localize("inv.col.value")} (${homeCurrency})</th>
                </tr>
                </thead>
                <tbody>
                @for(BalanceRow row : rows)
                    <tr data-href="/inventory/movements?itemId=${row.itemId().toString()}&warehouseId=${row.warehouseId().toString()}">
                        <td>${row.itemName()}</td>
                        <td>${row.warehouseName()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(row.qty(), row.unit())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.avgCost())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong"><b>${Fmt.money(row.value())}</b></td>
                    </tr>
                @endfor
                </tbody>
        `)
    @endif
    <%-- Саҳифалаш (DEC-105б): филтр (омбор/қидирув/категория/нол)
         линкларда сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/inventory/balances", filterQuery = filterQuery, msg = msg)
`)
