@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.inventory.service.InventoryValuationService
@import com.averpo.erp.inventory.web.InventoryValuationController.ItemGroup
@import com.averpo.erp.inventory.web.InventoryValuationController.WarehouseRow
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<ItemGroup> groups
@param InventoryValuationService.Report report
@param List<Warehouse> warehouses
@param String selectedWarehouseId
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.invValuation", title = msg.lookup("invval.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-1.5 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("invval.heading")}</h1>
    <%-- U3: QBO услуби - катакларга код ёзилмайди, тепада битта ёзув --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>

    <form method="get" action="/reports/inventory-valuation"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("bs.asOf")}
            <input type="date" name="asOf" value="${report.asOf().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
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
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tb.show")}</button>
    </form>

    <%-- GL солиштируви фақат фильтрсиз маъноли (GL'да омбор кесими йўқ) --%>
    @if(selectedWarehouseId.isEmpty() && report.glBalance() != null && !report.matchesGl())
        <p class="mb-4 rounded-radius border border-warning bg-warning/10 px-3 py-2.5 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("invval.glMismatch", Fmt.money(report.companyValue()), Fmt.money(report.glBalance()))}
        </p>
    @endif

    @if(groups.isEmpty())
        <p class="text-current/60">${msg.localize("inv.empty")}</p>
    @else
            @template.shared.table(cls = "min-w-[640px]", c = @`
                <thead>
                <tr>
                    <th>${msg.localize("inv.col.item")}</th>
                    <th class="text-right">${msg.localize("inv.col.qty")}</th>
                    <th class="text-right">${msg.localize("inv.col.avgCost")} (${homeCurrency})</th>
                    <th class="text-right">${msg.localize("inv.col.value")} (${homeCurrency})</th>
                </tr>
                </thead>
                <tbody>
                @for(ItemGroup group : groups)
                    <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                        <td>${group.itemName()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(group.qty(), group.unit())}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">@if(group.avgCost() != null)${Fmt.money(group.avgCost())}@else-@endif</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(group.value())}</td>
                    </tr>
                    @for(WarehouseRow row : group.warehouses())
                        <%-- T9: омбор сатри босилса шу item + омбор ҳаракатлари --%>
                        <tr data-href="/inventory/movements?itemId=${row.itemId().toString()}&warehouseId=${row.warehouseId().toString()}">
                            <td class="pl-7!">${row.warehouseName()}</td>
                            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(row.qty(), group.unit())}</td>
                            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">@if(row.avgCost() != null)${Fmt.money(row.avgCost())}@else-@endif</td>
                            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.value())}</td>
                        </tr>
                    @endfor
                @endfor
                </tbody>
                <tfoot>
                <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                    <td colspan="3">${msg.localize("tb.total")}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(report.totalValue())}</td>
                </tr>
                </tfoot>
        `)
        @if(selectedWarehouseId.isEmpty() && report.glBalance() != null && report.matchesGl())
            <p class="mt-2 text-[.85rem] text-success">${msg.localize("invval.glMatches", Fmt.money(report.glBalance()))}</p>
        @endif
        <p class="mt-2 text-[.85rem] text-current/60">${msg.localize("invval.note")}</p>
    @endif
`)
