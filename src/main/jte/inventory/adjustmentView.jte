@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.StockAdjustment
@import com.averpo.erp.inventory.domain.StockAdjustmentLine
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param StockAdjustment act
@param Map<UUID, String> itemNames
@param String homeCurrency
@param Boolean hasGl = false
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

<%-- Инвентаризация акти кўриши (DEC-093): сатрлар + GL ҳавола (080) +
     reverse (қарши-акт prefill). GL линки фақат актда нолдан фарқли
     қиймат бўлса кўринади (hasGl - controller ҳисоблайди). --%>
@template.layout.main(title = act.getAdjNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "stockAdjustment") ${act.getAdjNumber()}</h1>
        <a href="/inventory/adjustments" class="text-primary underline dark:text-primary-dark">${msg.localize("inv.adjust.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.warehouse")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getWarehouse().getName()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getAdjDate().toString()}</span>
        </div>
        @if(act.getExternalRef() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.externalRef")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getExternalRef()}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.adjust.netEffect")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(act.getTotalCost())} ${homeCurrency}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = act.getStatus().name(), msg = msg)</span>
        </div>
    </div>

    @if(hasGl != null && hasGl)
        @template.shared.jeLink(sourceType = "INVENTORY", id = act.getId().toString(), msg = msg)
    @endif

    @template.shared.table(cls = "min-w-[640px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("inv.col.item")}</th>
            <th class="text-right">${msg.localize("inv.adjust.newQty")}</th>
            <th class="text-right">${msg.localize("inv.adjust.delta")}</th>
            <th class="text-right">${msg.localize("inv.col.unitCost")} (${homeCurrency})</th>
            <th class="text-right">${msg.localize("inv.adjust.lineEffect")} (${homeCurrency})</th>
        </tr>
        </thead>
        <tbody>
        @for(StockAdjustmentLine line : act.getLines())
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${itemNames.getOrDefault(line.getItemId(), "-")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(line.getNewQty(), null)}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getDeltaQty().signum() > 0)
                        <span class="text-success">+${Fmt.qty(line.getDeltaQty(), null)}</span>
                    @elseif(line.getDeltaQty().signum() < 0)
                        <span class="text-danger">${Fmt.qty(line.getDeltaQty(), null)}</span>
                    @else
                        -
                    @endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${line.getUnitCost() == null ? "-" : Fmt.money(line.getUnitCost())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getLineCost())}</td>
            </tr>
        @endfor
        </tbody>
    `)

    @if(act.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${act.getMemo()}</p>
    @endif

    <%-- Reverse = қарши-акт prefill (spec): янги форма тескари delta билан --%>
    @if(canEdit != null && canEdit && act.getStatus() == StockAdjustment.Status.POSTED)
        <a href="/inventory/adjustments/${act.getId().toString()}/reverse" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-warning bg-warning px-4 py-2 text-center text-sm font-medium tracking-wide text-on-warning transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-warning active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">${msg.localize("inv.adjust.reverse")}</a>
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент (Ҳабиба 094) --%>
    <div hx-get="/attachments/STOCK_ADJUSTMENT/${act.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
