@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.Estimate
@import com.averpo.erp.sales.domain.EstimateStatus
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<Estimate> estimates
@param Map<UUID, String> customerNames
@param List<ContactRef> customers = null
@param org.springframework.data.domain.Page<Estimate> page = null
@param String filterQuery = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String status = ""
@param String from = ""
@param String to = ""
@param String customerId = ""
@param String q = ""
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.estimates", title = msg.lookup("est.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("est.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/estimates/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + мижоз + матн --%>
    @template.shared.listFilter(action = "/estimates", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(EstimateStatus st : EstimateStatus.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize(st.titleKey())}
                    </option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.customer")}
            @template.shared.combobox(name = "customerId", msg = msg,
                    value = customerId,
                    placeholder = msg.lookup("filter.all"),
                    cls = "mt-1 w-56 max-w-56",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("filter.all"))
                        @if(customers != null)
                            @for(ContactRef customer : customers)
                                @template.shared.comboOption(value = customer.id().toString(),
                                        label = customer.displayName(),
                                        selected = customer.id().toString().equals(customerId))
                            @endfor
                        @endif
                    `)
        </label>
    `)

    @if(estimates.isEmpty())
        <p class="text-current/60">${msg.localize("est.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[760px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("est.col.number")}</th>
                <th>${msg.localize("est.col.customer")}</th>
                <th>${msg.localize("est.col.date")}</th>
                <th>${msg.localize("est.col.expiration")}</th>
                <th class="text-right">${msg.localize("est.col.total")}</th>
                <th>${msg.localize("est.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(Estimate estimate : estimates)
                <tr data-href="/estimates/${estimate.getId().toString()}">
                    <td><a href="/estimates/${estimate.getId().toString()}">${estimate.getEstimateNumber()}</a></td>
                    <td>${customerNames.getOrDefault(estimate.getCustomerId(), "-")}</td>
                    <td>${estimate.getEstimateDate().toString()}</td>
                    <td>${estimate.getExpirationDate() != null ? estimate.getExpirationDate().toString() : ""}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(estimate.getTotal())} ${estimate.getCurrency().getCode()}</td>
                    <td>
                        @template.shared.badge(value = estimate.getStatus().name(), family = "est.status", msg = msg)
                    </td>
                    <td class="text-center">
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/estimates/${estimate.getId().toString()}">${msg.localize("common.view")}</a>
                            @if(canEdit != null && canEdit && (estimate.getStatus() == EstimateStatus.PENDING || estimate.getStatus() == EstimateStatus.ACCEPTED))
                                <a href="/estimates/${estimate.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
                            @endif
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (Beruniy-perf1); статус филтри линкларда сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/estimates", filterQuery = filterQuery, msg = msg)
`)
