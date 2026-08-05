@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.Bill
@import com.averpo.erp.purchase.domain.BillStatus
@import com.averpo.erp.purchase.domain.PaymentStatus
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<Bill> bills
@param Map<UUID, String> vendorNames
@param List<ContactRef> vendors = null
@param org.springframework.data.domain.Page<Bill> page = null
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
@param String vendorId = ""
@param String filterVendorName = null
@param Boolean filterOpen = false
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.bills", title = msg.lookup("bill.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("bill.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/bills/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (DEC-068): давр + статус + таъминотчи + матн --%>
    @template.shared.listFilter(action = "/bills", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(BillStatus st : BillStatus.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize("status." + st.name())}
                    </option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.vendor")}
            @template.shared.combobox(name = "vendorId", msg = msg,
                    value = vendorId,
                    placeholder = msg.lookup("filter.all"),
                    cls = "mt-1 w-56 max-w-56",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("filter.all"))
                        @if(vendors != null)
                            @for(ContactRef vendor : vendors)
                                @template.shared.comboOption(value = vendor.id().toString(),
                                        label = vendor.displayName(),
                                        selected = vendor.id().toString().equals(vendorId))
                            @endfor
                        @endif
                    `)
        </label>
    `)

    <%-- T11: aging'дан «фақат очиқ» drill-down чипи; оддий vendor филтри
         энди юқоридаги select'да - чип фақат open режимда --%>
    @if(filterOpen != null && filterOpen && filterVendorName != null)
        <p class="mb-4 text-current/60">
            ${msg.localize("bill.filter.vendorOpen", filterVendorName)} -
            <a href="/bills" class="text-primary underline dark:text-primary-dark">${msg.localize("common.clearFilter")}</a>
        </p>
    @endif

    @if(bills.isEmpty())
        <p class="text-current/60">${msg.localize("bill.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[900px]", c = @`
            <%-- Устун саралаш (DEC-105б): whitelist'даги устунлар
                 sortth линк, қолганлари оддий th (vendor - ном бошқа
                 модулда, payStatus/actions - маъносиз). --%>
            <thead>
            <tr>
                @template.shared.sortth(key = "number", label = msg.lookup("bill.col.number"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                <th>${msg.localize("bill.col.vendor")}</th>
                @template.shared.sortth(key = "vendorInvoice", label = msg.lookup("bill.col.vendorInvoice"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "date", label = msg.lookup("bill.col.date"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "dueDate", label = msg.lookup("bill.col.dueDate"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "total", label = msg.lookup("bill.col.total"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery, cls = "text-right")
                @template.shared.sortth(key = "balance", label = msg.lookup("bill.col.balance"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery, cls = "text-right")
                @template.shared.sortth(key = "status", label = msg.lookup("bill.col.status"), baseUrl = "/bills", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                <th>${msg.localize("bill.col.payStatus")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(Bill bill : bills)
                <%-- T4: DRAFT - таҳрир формасига, акс ҳолда кўришга --%>
                <tr data-href="${bill.getStatus() == BillStatus.DRAFT ? "/bills/" + bill.getId() + "/edit" : "/bills/" + bill.getId()}">
                    <td><a href="/bills/${bill.getId().toString()}">${bill.getBillNumber()}</a></td>
                    <td>${vendorNames.getOrDefault(bill.getVendorId(), "-")}</td>
                    <td>${bill.getVendorInvoiceNumber()}</td>
                    <td>${bill.getBillDate().toString()}</td>
                    <td>${bill.getDueDate() != null ? bill.getDueDate().toString() : ""}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(bill.getTotal())} ${bill.getCurrency().getCode()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(bill.getBalanceDue())} ${bill.getCurrency().getCode()}</td>
                    <td>
                        @template.shared.badge(value = bill.getStatus().name(), msg = msg)
                    </td>
                    <td>
                        @if(bill.getStatus() == BillStatus.POSTED)
                            @template.shared.badge(value = bill.getPaymentStatus().name(), family = "paystatus", msg = msg)
                        @endif
                    </td>
                    <td class="text-center">
                        <%-- BA-007 P1a: Action менюси --%>
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/bills/${bill.getId().toString()}">${msg.localize("common.view")}</a>
                            @if(canEdit != null && canEdit && bill.getStatus() == BillStatus.DRAFT)
                                <a href="/bills/${bill.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
                            @endif
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (PERF-perf1); open drill-down'да page=null - чиқмайди.
         pageQuery = филтр + жорий sort (105б) - варақлаганда тартиб сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/bills", filterQuery = pageQuery, msg = msg)
`)
