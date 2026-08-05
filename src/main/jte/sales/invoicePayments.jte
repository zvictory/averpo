@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.domain.InvoicePayment
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<InvoicePayment> payments
@param Map<UUID, String> customerNames
@param List<ContactRef> customers = null
@param org.springframework.data.domain.Page<InvoicePayment> page = null
@param String filterQuery = ""
@param String from = ""
@param String to = ""
@param String status = ""
@param String customerId = ""
@param String q = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.invoicePayments", title = msg.lookup("rcpt.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("rcpt.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/invoice-payments/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + мижоз + матн --%>
    @template.shared.listFilter(action = "/invoice-payments", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(InvoicePayment.Status st : InvoicePayment.Status.values())
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
                            @for(ContactRef customer : customers)
                                @template.shared.comboOption(value = customer.id().toString(),
                                        label = customer.displayName(),
                                        selected = customer.id().toString().equals(customerId))
                            @endfor
                        @endif
                    `)
        </label>
    `)

    @if(payments.isEmpty())
        <p class="text-current/60">${msg.localize("rcpt.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[820px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("rcpt.col.number")}</th>
                <th>${msg.localize("rcpt.col.customer")}</th>
                <th>${msg.localize("rcpt.col.date")}</th>
                <th class="text-right">${msg.localize("rcpt.col.total")}</th>
                <th class="text-right">${msg.localize("rcpt.col.allocated")}</th>
                <th class="text-right">${msg.localize("rcpt.col.unallocated")}</th>
                <th>${msg.localize("rcpt.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(InvoicePayment payment : payments)
                <tr data-href="/invoice-payments/${payment.getId().toString()}">
                    <td><a href="/invoice-payments/${payment.getId().toString()}">${payment.getReceiptNumber()}</a></td>
                    <td>${customerNames.getOrDefault(payment.getCustomerId(), "-")}</td>
                    <td>${payment.getPaymentDate().toString()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(payment.getTotalAmount())} ${payment.getCurrency().getCode()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(payment.getAllocatedAmount())} ${payment.getCurrency().getCode()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                        @if(payment.getUnallocatedAmount().signum() > 0)
                            <span class="font-semibold text-amber-700 dark:text-amber-500">${Fmt.money(payment.getUnallocatedAmount())} ${payment.getCurrency().getCode()}</span>
                        @else
                            ${Fmt.money(payment.getUnallocatedAmount())} ${payment.getCurrency().getCode()}
                        @endif
                    </td>
                    <td>
                        @template.shared.badge(value = payment.getStatus().name(), msg = msg)
                    </td>
                    <td class="text-center">
                        <%-- Nargiza-007 P1a: Action менюси --%>
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/invoice-payments/${payment.getId().toString()}">${msg.localize("common.view")}</a>
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (Beruniy-perf1); линклар филтрни сақлайди --%>
    @template.shared.pagination(page = page, baseUrl = "/invoice-payments", filterQuery = filterQuery, msg = msg)
`)
