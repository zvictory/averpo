@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.StockTransfer
@import com.averpo.erp.inventory.domain.StockTransferLine
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param StockTransfer act
@param Map<UUID, String> itemNames
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

<%-- Кўчириш акти кўриши (DEC-093): сатрлар + reverse (қарши-акт
     prefill, манба/манзил алмашади). GL линки ЙЎҚ - transfer GL'сиз. --%>
@template.layout.main(title = act.getWtrNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "stockTransfer") ${act.getWtrNumber()}</h1>
        <a href="/inventory/transfers" class="text-primary underline dark:text-primary-dark">${msg.localize("inv.transfer.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- DEC-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.transfer.from")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getFromWarehouse().getName()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.transfer.to")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getToWarehouse().getName()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getWtrDate().toString()}</span>
        </div>
        @if(act.getExternalRef() != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.externalRef")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${act.getExternalRef()}</span>
            </div>
        @endif
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("inv.col.value")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${Fmt.money(act.getTotalCost())} ${homeCurrency}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("bt.col.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = act.getStatus().name(), msg = msg)</span>
        </div>
    </div>

    @template.shared.table(cls = "min-w-[520px]", wrap = "mb-4", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("inv.col.item")}</th>
            <th class="text-right">${msg.localize("inv.col.qty")}</th>
            <th class="text-right">${msg.localize("inv.col.value")} (${homeCurrency})</th>
        </tr>
        </thead>
        <tbody>
        @for(StockTransferLine line : act.getLines())
            <tr>
                <td>${line.getLineNo()}</td>
                <td>${itemNames.getOrDefault(line.getItemId(), "-")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.qty(line.getQuantity(), null)}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(line.getLineCost())}</td>
            </tr>
        @endfor
        </tbody>
    `)

    @if(act.getMemo() != null)
        <p style="margin-bottom:1rem;white-space:pre-line;">${act.getMemo()}</p>
    @endif

    @if(canEdit != null && canEdit && act.getStatus() == StockTransfer.Status.POSTED)
        <a href="/inventory/transfers/${act.getId().toString()}/reverse" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-warning bg-warning px-4 py-2 text-center text-sm font-medium tracking-wide text-on-warning transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-warning active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">${msg.localize("inv.transfer.reverse")}</a>
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент (Ҳабиба 094) --%>
    <div hx-get="/attachments/STOCK_TRANSFER/${act.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
