@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.PurchaseOrder
@import com.averpo.erp.purchase.domain.PurchaseOrderStatus
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<PurchaseOrder> orders
@param Map<UUID, String> vendorNames
@param List<ContactRef> vendors = null
@param org.springframework.data.domain.Page<PurchaseOrder> page = null
@param String filterQuery = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String status = ""
@param String from = ""
@param String to = ""
@param String vendorId = ""
@param String q = ""
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.purchaseOrders", title = msg.lookup("po.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("po.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/purchase-orders/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (DEC-068): давр + статус + таъминотчи + матн --%>
    @template.shared.listFilter(action = "/purchase-orders", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(PurchaseOrderStatus st : PurchaseOrderStatus.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize(st.titleKey())}
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

    @if(orders.isEmpty())
        <p class="text-current/60">${msg.localize("po.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[760px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("po.col.number")}</th>
                <th>${msg.localize("po.col.vendor")}</th>
                <th>${msg.localize("po.col.date")}</th>
                <th>${msg.localize("po.col.expected")}</th>
                <th class="text-right">${msg.localize("po.col.total")}</th>
                <th>${msg.localize("po.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(PurchaseOrder po : orders)
                <tr data-href="/purchase-orders/${po.getId().toString()}">
                    <td><a href="/purchase-orders/${po.getId().toString()}">${po.getPoNumber()}</a></td>
                    <td>${vendorNames.getOrDefault(po.getVendorId(), "-")}</td>
                    <td>${po.getPoDate().toString()}</td>
                    <td>${po.getExpectedDate() != null ? po.getExpectedDate().toString() : ""}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(po.getTotal())} ${po.getCurrency().getCode()}</td>
                    <td>
                        @template.shared.badge(value = po.getStatus().name(), family = "po.status", msg = msg)
                    </td>
                    <td class="text-center">
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/purchase-orders/${po.getId().toString()}">${msg.localize("common.view")}</a>
                            @if(canEdit != null && canEdit && po.getStatus() == PurchaseOrderStatus.OPEN)
                                <a href="/purchase-orders/${po.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
                            @endif
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (PERF-perf1); статус филтри линкларда сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/purchase-orders", filterQuery = filterQuery, msg = msg)
`)
