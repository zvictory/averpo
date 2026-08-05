@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.MovementType
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.inventory.web.InventoryController.MovementRow
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<MovementRow> rows
@param List<Warehouse> warehouses
@param MovementType[] movementTypes = new MovementType[0]
@param String selectedWarehouseId
@param String selectedItemId = ""
@param String selectedItemName = null
@param String selectedType = ""
@param String from = ""
@param String to = ""
@param String docNumber = ""
@param String homeCurrency
@param org.springframework.data.domain.Page<?> page = null
@param String filterQuery = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.stockMovements", title = msg.lookup("inv.movements.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-4 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("inv.movements.title")}</h1>

    @template.shared.alert(message = message)

    <%-- Мукаммал филтр (Arbitr-093): тур/омбор/item/сана оралиғи/ҳужжат.
         Item филтри (T9) hidden orqali сақланади (қолдиқлардан drill-down). --%>
    <form method="get" action="/inventory/movements" style="margin-bottom:1rem;display:flex;gap:.75rem;align-items:end;flex-wrap:wrap;">
        @if(!selectedItemId.isEmpty())
            <input type="hidden" name="itemId" value="${selectedItemId}">
        @endif
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.type")}
            <select name="type" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${selectedType.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(MovementType type : movementTypes)
                    <option value="${type.name()}" selected="${type.name().equals(selectedType)}">
                        ${msg.localize(type.titleKey())}
                    </option>
                @endfor
            </select>
        </label>
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
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.from")}
            <input type="date" name="from" value="${from}" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.to")}
            <input type="date" name="to" value="${to}" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("inv.filter.docNumber")}
            <input name="docNumber" value="${docNumber}" placeholder="ADJ-2026-..."
                   class="mt-1 block w-36 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("filter.apply")}</button>
        @if(selectedItemName != null)
            <span class="text-[.9rem] text-current/60">
                ${msg.localize("inv.filter.item", selectedItemName)} -
                <a href="/inventory/movements" class="text-primary underline dark:text-primary-dark">${msg.localize("common.clearFilter")}</a>
            </span>
        @endif
    </form>

    @if(rows.isEmpty())
        <p class="text-current/60">${msg.localize("inv.empty")}</p>
    @else
            @template.shared.table(cls = "min-w-[860px]", c = @`
                <thead>
                <tr>
                    <th>${msg.localize("inv.col.date")}</th>
                    <th>${msg.localize("inv.col.type")}</th>
                    <th>${msg.localize("inv.col.item")}</th>
                    <th>${msg.localize("inv.col.warehouse")}</th>
                    <th>${msg.localize("inv.col.counterpart")}</th>
                    <th class="text-right">${msg.localize("inv.col.qty")}</th>
                    <th class="text-right">${msg.localize("inv.col.unitCost")} (${homeCurrency})</th>
                    <th class="text-right">${msg.localize("inv.col.total")} (${homeCurrency})</th>
                    <th>${msg.localize("inv.col.memo")}</th>
                </tr>
                </thead>
                <tbody>
                @for(MovementRow row : rows)
                    <tr data-href="${row.docUrl()}">
                        <td class="whitespace-nowrap">${row.date().toString()}</td>
                        <td>
                            @if(row.docUrl() != null)
                                <a href="${row.docUrl()}" class="font-medium text-primary no-underline hover:underline dark:text-primary-dark">${msg.localize(row.typeKey())}</a>
                            @else
                                ${msg.localize(row.typeKey())}
                            @endif
                        </td>
                        <td>${row.itemName()}</td>
                        <td>${row.warehouseName()}</td>
                        <td>${row.counterpartName()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(row.qty(), row.unit())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.unitCost())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.totalCost())}</td>
                        <td>${row.memo()}</td>
                    </tr>
                @endfor
                </tbody>
        `)
    @endif
    @template.shared.pagination(page = page, baseUrl = "/inventory/movements", filterQuery = filterQuery, msg = msg)
`)
