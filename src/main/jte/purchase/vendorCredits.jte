@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.purchase.domain.VendorCredit
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<VendorCredit> credits
@param Map<UUID, String> vendorNames
@param List<ContactRef> vendors = null
@param org.springframework.data.domain.Page<VendorCredit> page = null
@param String filterQuery = ""
@param String from = ""
@param String to = ""
@param String status = ""
@param String vendorId = ""
@param String q = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.vendorCredits", title = msg.lookup("vc.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("vc.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/vendor-credits/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (DEC-068): давр + статус + таъминотчи + матн --%>
    @template.shared.listFilter(action = "/vendor-credits", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(VendorCredit.Status st : VendorCredit.Status.values())
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

    @if(credits.isEmpty())
        <p class="text-current/60">${msg.localize("vc.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[820px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bt.col.number")}</th>
                <th>${msg.localize("bill.col.vendor")}</th>
                <th>${msg.localize("bt.col.date")}</th>
                <th class="text-right">${msg.localize("bt.col.total")}</th>
                <th class="text-right">${msg.localize("cm.col.open")}</th>
                <th>${msg.localize("bt.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(VendorCredit credit : credits)
                <tr data-href="/vendor-credits/${credit.getId().toString()}">
                    <td><a href="/vendor-credits/${credit.getId().toString()}">${credit.getVcNumber()}</a></td>
                    <td>${vendorNames.getOrDefault(credit.getVendorId(), "-")}</td>
                    <td>${credit.getVcDate().toString()}</td>
                    <td class="text-right whitespace-nowrap">${Fmt.money(credit.getTotal())} ${credit.getCurrency().getCode()}</td>
                    <td class="text-right whitespace-nowrap">${Fmt.money(credit.getOpenBalance())} ${credit.getCurrency().getCode()}</td>
                    <td>
                        @template.shared.badge(value = credit.getStatus().name(), msg = msg)
                    </td>
                    <td class="text-center">
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/vendor-credits/${credit.getId().toString()}">${msg.localize("common.view")}</a>
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш - рўйхат саҳифаланган туғилади (PERF-perf1 қолипи) --%>
    @template.shared.pagination(page = page, baseUrl = "/vendor-credits", filterQuery = filterQuery, msg = msg)
`)
