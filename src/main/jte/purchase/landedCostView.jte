@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.StockMovement
@import com.averpo.erp.purchase.domain.LandedCostAllocation
@import com.averpo.erp.purchase.domain.LandedCostAllocationLine
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param LandedCostAllocation allocation
@param List<LandedCostAllocationLine> lines
@param Map<UUID, StockMovement> receipts
@param Map<UUID, String> itemNames
@param Map<UUID, String> billNumbers
@param String today
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = allocation.getAllocationNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.35rem;flex-wrap:wrap;gap:.5rem;">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "landedCost") ${allocation.getAllocationNumber()}</h1>
        <a href="/landed-costs" class="text-primary underline dark:text-primary-dark">${msg.localize("lc.view.back")}</a>
    </div>
    <%-- U3 услуби: LC home валютада ишлайди - катакларга код ёзилмайди,
         тепада битта ёзув (UI-001) --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}</p>

    @template.shared.alert(message = message, error = error)

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("lc.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${allocation.getAllocationDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("lc.col.amount")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(allocation.getTotalAmount())}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("lc.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = allocation.getStatus().name(), msg = msg)</span>
        </div>
    </div>

    <%-- DEC-080: ҳужжатдан GL ёзувига (JE) линк - POSTED/REVERSED'да --%>
    @if(allocation.getStatus() == LandedCostAllocation.Status.POSTED || allocation.getStatus() == LandedCostAllocation.Status.REVERSED)
        @template.shared.jeLink(sourceType = "LANDED_COST", id = allocation.getId().toString(), msg = msg)
    @endif

    @if(allocation.getMemo() != null)
        <p class="mb-4">${allocation.getMemo()}</p>
    @endif

    @template.shared.table(cls = "min-w-[860px]", wrap = "mb-5", c = @`
        <thead>
        <tr>
            <th>${msg.localize("lc.col.bill")}</th>
            <th>${msg.localize("lc.col.item")}</th>
            <th>${msg.localize("lc.col.warehouse")}</th>
            <th class="text-right">${msg.localize("lc.col.qty")}</th>
            <th class="text-right">${msg.localize("lc.col.share")}</th>
            <th class="text-right">${msg.localize("lc.col.toInventory")}</th>
            <th class="text-right">${msg.localize("lc.col.toCogs")}</th>
        </tr>
        </thead>
        <tbody>
        @for(LandedCostAllocationLine line : lines)
            !{var receipt = receipts.get(line.getMovementId());}
            <tr>
                <td>${receipt != null ? billNumbers.getOrDefault(receipt.getReferenceId(), "-") : "-"}</td>
                <td>${receipt != null ? itemNames.getOrDefault(receipt.getItemId(), "-") : "-"}</td>
                <td>${receipt != null ? receipt.getWarehouse().getName() : "-"}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(receipt != null)${Fmt.qty(receipt.getQuantity())}@endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getAmount())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getInventoryShare())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getCogsShare())}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <tr>
            <td colspan="4" class="text-right font-bold">${msg.localize("lc.col.amount")}:</td>
            <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(allocation.getTotalAmount())}</td>
            <td colspan="2"></td>
        </tr>
        </tfoot>
    `)

    @if(canEdit != null && canEdit && allocation.getStatus() == LandedCostAllocation.Status.POSTED)
        @template.shared.reverseForm(action = "/landed-costs/" + allocation.getId().toString() + "/reverse",
            confirmKey = "lc.view.reverseConfirm", labelPrefix = "bill.view",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/LANDED_COST/${allocation.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
