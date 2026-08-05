@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.LandedCostAllocation
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<LandedCostAllocation> allocations
@param String homeCurrency
@param String from = ""
@param String to = ""
@param String status = ""
@param String q = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.landedCosts", title = msg.lookup("lc.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.35rem;flex-wrap:wrap;gap:.5rem;">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("lc.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/landed-costs/new")
        @endif
    </div>
    <%-- U3 услуби: LC home валютада ишлайди - катакларга код ёзилмайди,
         тепада битта ёзув (UI-001) --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}</p>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (DEC-068): давр + статус + матн (контактсиз) --%>
    @template.shared.listFilter(action = "/landed-costs", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(LandedCostAllocation.Status st : LandedCostAllocation.Status.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize("status." + st.name())}
                    </option>
                @endfor
            </select>
        </label>
    `)

    @if(allocations.isEmpty())
        <p class="text-current/60">${msg.localize("lc.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[640px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("lc.col.number")}</th>
                <th>${msg.localize("lc.col.date")}</th>
                <th class="text-right">${msg.localize("lc.col.amount")}</th>
                <th>${msg.localize("lc.col.memo")}</th>
                <th>${msg.localize("lc.col.status")}</th>
            </tr>
            </thead>
            <tbody>
            @for(LandedCostAllocation allocation : allocations)
                <tr>
                    <td><a href="/landed-costs/${allocation.getId().toString()}">${allocation.getAllocationNumber()}</a></td>
                    <td>${allocation.getAllocationDate().toString()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(allocation.getTotalAmount())}</td>
                    <td>${allocation.getMemo()}</td>
                    <td>
                        @template.shared.badge(value = allocation.getStatus().name(), msg = msg)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
`)
