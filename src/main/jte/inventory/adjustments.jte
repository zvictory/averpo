@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.StockAdjustment
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<StockAdjustment> acts
@param List<Warehouse> warehouses
@param String selectedWarehouseId
@param String from = ""
@param String to = ""
@param org.springframework.data.domain.Page<?> page = null
@param String filterQuery = ""
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = msg.lookup("inv.adjust.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("inv.adjust.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/inventory/adjustments/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <form method="get" action="/inventory/adjustments" style="margin-bottom:1rem;display:flex;gap:.75rem;align-items:end;flex-wrap:wrap;">
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
        <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("filter.apply")}</button>
    </form>

    @if(acts.isEmpty())
        <p class="text-current/60">${msg.localize("inv.adjust.empty")}</p>
    @else
            @template.shared.table(cls = "min-w-[640px]", c = @`
                <thead>
                <tr>
                    <th>${msg.localize("bt.col.number")}</th>
                    <th>${msg.localize("inv.col.date")}</th>
                    <th>${msg.localize("inv.col.warehouse")}</th>
                    <th class="text-right">${msg.localize("inv.adjust.netEffect")} (${homeCurrency})</th>
                    <th>${msg.localize("bt.col.status")}</th>
                </tr>
                </thead>
                <tbody>
                @for(StockAdjustment act : acts)
                    <tr data-href="/inventory/adjustments/${act.getId().toString()}">
                        <td><a href="/inventory/adjustments/${act.getId().toString()}">${act.getAdjNumber()}</a></td>
                        <td class="whitespace-nowrap">${act.getAdjDate().toString()}</td>
                        <td>${act.getWarehouse().getName()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(act.getTotalCost())}</td>
                        <td>
                            @template.shared.badge(value = act.getStatus().name(), msg = msg)
                        </td>
                    </tr>
                @endfor
                </tbody>
        `)
    @endif
    @template.shared.pagination(page = page, baseUrl = "/inventory/adjustments", filterQuery = filterQuery, msg = msg)
`)
