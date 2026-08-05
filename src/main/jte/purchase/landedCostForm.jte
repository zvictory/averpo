@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.StockMovement
@import com.averpo.erp.purchase.web.LandedCostForm
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.Set
@import java.util.UUID
@param LandedCostForm form
@param List<StockMovement> receipts
@param Set<String> selectedIds
@param Map<UUID, String> itemNames
@param Map<UUID, String> billNumbers
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null

@template.layout.form(title = msg.lookup("lc.form.title"), closeHref = "/landed-costs", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="lcForm" method="post" action="/landed-costs">
        @template.shared.csrf(csrf = csrf)
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("lc.form.date")}
                <input type="date" name="allocationDate" required
                       value="${form.getAllocationDate() != null ? form.getAllocationDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1">${msg.localize("lc.form.amount")} (${homeCurrency}) @template.shared.help(key = "field.lcAllocation", msg = msg)</span>
                <input name="totalAmount" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" required size="14"
                       value="${form.getTotalAmount() != null ? form.getTotalAmount() : ""}"
                      >
                <small class="block text-current/60">${msg.localize("lc.form.amountNote")}</small>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("lc.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("lc.form.receipts")}</h3>
        <p class="mb-2 text-sm text-current/60">${msg.localize("lc.form.receiptsNote")}</p>
        @if(receipts.isEmpty())
            <p class="text-current/60">${msg.localize("lc.form.noReceipts")}</p>
        @else
            @template.shared.table(cls = "min-w-[760px]", wrap = "mb-3", c = @`
                <thead>
                <tr>
                    <th class="w-10"></th>
                    <th>${msg.localize("lc.col.date")}</th>
                    <th>${msg.localize("lc.col.bill")}</th>
                    <th>${msg.localize("lc.col.item")}</th>
                    <th>${msg.localize("lc.col.warehouse")}</th>
                    <th class="text-right">${msg.localize("lc.col.qty")}</th>
                    <th class="text-right">${msg.localize("lc.col.value")}</th>
                </tr>
                </thead>
                <tbody>
                @for(StockMovement receipt : receipts)
                    <tr>
                        <td class="text-center">
                            <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="movementIds" value="${receipt.getId().toString()}"
                                   checked="${selectedIds.contains(receipt.getId().toString())}">
                        </td>
                        <td>${receipt.getMovementDate().toString()}</td>
                        <td>${billNumbers.getOrDefault(receipt.getReferenceId(), "-")}</td>
                        <td>${itemNames.getOrDefault(receipt.getItemId(), "-")}</td>
                        <td>${receipt.getWarehouse().getName()}</td>
                        <td class="text-right">${Fmt.qty(receipt.getQuantity())}</td>
                        <td class="text-right">${Fmt.money(receipt.getTotalCost())}</td>
                    </tr>
                @endfor
                </tbody>
            `)
        @endif
    </form>
`, actions = @`
    <a href="/landed-costs" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="lcForm">
            ${msg.localize("lc.form.save")}</button>
    </div>
`)
