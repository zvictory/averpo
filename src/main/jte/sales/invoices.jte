@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.Invoice
@import com.averpo.erp.sales.domain.InvoiceStatus
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<Invoice> invoices
@param Map<UUID, String> customerNames
@param List<Contact> customers = null
@param org.springframework.data.domain.Page<Invoice> page = null
@param String filterQuery = ""
@param String pageQuery = ""
@param String sortKey = null
@param String sortDir = null
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String status = ""
@param String from = ""
@param String to = ""
@param String q = ""
@param String customerId = ""
@param String filterCustomerName = null
@param Boolean filterOpen = false
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.invoices", title = msg.lookup("sinv.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("sinv.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/invoices/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + мижоз + матн --%>
    @template.shared.listFilter(action = "/invoices", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(InvoiceStatus st : InvoiceStatus.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize("status." + st.name())}
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
                            @for(Contact customer : customers)
                                @template.shared.comboOption(value = customer.getId().toString(),
                                        label = customer.getDisplayName(),
                                        selected = customer.getId().toString().equals(customerId))
                            @endfor
                        @endif
                    `)
        </label>
    `)

    <%-- AR aging'дан «фақат очиқ» drill-down чипи (T11); оддий мижоз
         филтри энди юқоридаги select'да кўринади - чип фақат open режимда --%>
    @if(filterOpen != null && filterOpen && filterCustomerName != null)
        <p class="mb-4 text-current/60">
            ${msg.localize("sinv.filter.customerOpen", filterCustomerName)} -
            <a href="/invoices" class="text-primary underline dark:text-primary-dark">${msg.localize("common.clearFilter")}</a>
        </p>
    @endif

    @if(invoices.isEmpty())
        <p class="text-current/60">${msg.localize("sinv.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[860px]", c = @`
            <%-- Устун саралаш (ARBITR-105б): whitelist'даги устунлар
                 sortth линк, қолганлари оддий th (customer - ном бошқа
                 модулда, payStatus/actions - маъносиз). --%>
            <thead>
            <tr>
                @template.shared.sortth(key = "number", label = msg.lookup("sinv.col.number"), baseUrl = "/invoices", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                <th>${msg.localize("sinv.col.customer")}</th>
                @template.shared.sortth(key = "date", label = msg.lookup("sinv.col.date"), baseUrl = "/invoices", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "dueDate", label = msg.lookup("sinv.col.dueDate"), baseUrl = "/invoices", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "total", label = msg.lookup("sinv.col.total"), baseUrl = "/invoices", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery, cls = "text-right")
                @template.shared.sortth(key = "balance", label = msg.lookup("sinv.col.balance"), baseUrl = "/invoices", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery, cls = "text-right")
                @template.shared.sortth(key = "status", label = msg.lookup("sinv.col.status"), baseUrl = "/invoices", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                <th>${msg.localize("sinv.col.payStatus")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(Invoice invoice : invoices)
                <%-- T4 паттерни: DRAFT - таҳрир формасига, акс ҳолда кўришга --%>
                <tr data-href="${invoice.getStatus() == InvoiceStatus.DRAFT ? "/invoices/" + invoice.getId() + "/edit" : "/invoices/" + invoice.getId()}">
                    <td><a href="/invoices/${invoice.getId().toString()}">${invoice.getInvoiceNumber()}</a></td>
                    <td>${customerNames.getOrDefault(invoice.getCustomerId(), "-")}</td>
                    <td>${invoice.getInvoiceDate().toString()}</td>
                    <td>${invoice.getDueDate() != null ? invoice.getDueDate().toString() : ""}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(invoice.getTotal())} ${invoice.getCurrency().getCode()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(invoice.getBalanceDue())} ${invoice.getCurrency().getCode()}</td>
                    <td>
                        @template.shared.badge(value = invoice.getStatus().name(), msg = msg)
                    </td>
                    <td>
                        @if(invoice.getStatus() == InvoiceStatus.POSTED)
                            @template.shared.badge(value = invoice.getPaymentStatus().name(), family = "paystatus", msg = msg)
                        @endif
                    </td>
                    <td class="text-center">
                        <%-- Nargiza-007 P1a: Action менюси --%>
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/invoices/${invoice.getId().toString()}">${msg.localize("common.view")}</a>
                            @if(canEdit != null && canEdit && invoice.getStatus() == InvoiceStatus.DRAFT)
                                <a href="/invoices/${invoice.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
                            @endif
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (Beruniy-perf1); open drill-down'да page=null - чиқмайди.
         pageQuery = филтр + жорий sort (105б) - варақлаганда тартиб сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/invoices", filterQuery = pageQuery, msg = msg)
`)
