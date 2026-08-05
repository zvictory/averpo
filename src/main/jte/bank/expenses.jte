@import com.averpo.erp.bank.domain.BankTransaction
@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<BankTransaction> expenses
@param Map<UUID, String> accountNames
@param Map<UUID, String> contactNames
@param org.springframework.data.domain.Page<BankTransaction> page = null
@param List<ContactRef> payees = null
@param String from = ""
@param String to = ""
@param String status = ""
@param String q = ""
@param String contactId = ""
@param String filterQuery = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.expenses", title = msg.lookup("expense.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("expense.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/expenses/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + Олувчи (payee) + матн --%>
    @template.shared.listFilter(action = "/expenses", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(BankTransaction.Status st : BankTransaction.Status.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize("status." + st.name())}
                    </option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.payee")}
            @template.shared.combobox(name = "contactId", msg = msg,
                    value = contactId,
                    placeholder = msg.lookup("filter.all"),
                    cls = "mt-1 w-56 max-w-56",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("filter.all"))
                        @if(payees != null)
                            @for(ContactRef payee : payees)
                                @template.shared.comboOption(value = payee.id().toString(),
                                        label = payee.displayName(),
                                        selected = payee.id().toString().equals(contactId))
                            @endfor
                        @endif
                    `)
        </label>
    `)

    @if(expenses.isEmpty())
        <p class="text-current/60">${msg.localize("expense.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[820px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bt.col.number")}</th>
                <th>${msg.localize("expense.col.payee")}</th>
                <th>${msg.localize("expense.col.account")}</th>
                <th>${msg.localize("bt.col.date")}</th>
                <th class="text-right">${msg.localize("bt.col.total")}</th>
                <th>${msg.localize("bt.col.memo")}</th>
                <th>${msg.localize("bt.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(BankTransaction txn : expenses)
                <tr data-href="/expenses/${txn.getId().toString()}">
                    <td><a href="/expenses/${txn.getId().toString()}">${txn.getTxnNumber()}</a></td>
                    <td>
                        @if(txn.getContactId() != null)
                            ${contactNames.getOrDefault(txn.getContactId(), "-")}
                        @endif
                    </td>
                    <td>${accountNames.getOrDefault(txn.getBankAccountId(), "-")}</td>
                    <td>${txn.getTxnDate().toString()}</td>
                    <td class="text-right whitespace-nowrap">
                        ${Fmt.money(txn.getTotal())} ${txn.getCurrency().getCode()}
                    </td>
                    <td>${txn.getMemo()}</td>
                    <td>
                        @template.shared.badge(value = txn.getStatus().name(), msg = msg)
                    </td>
                    <td class="text-center">
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/expenses/${txn.getId().toString()}">${msg.localize("common.view")}</a>
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (Beruniy-perf1); filterQuery жорий филтрни варақлашда сақлайди --%>
    @template.shared.pagination(page = page, baseUrl = "/expenses", filterQuery = filterQuery, msg = msg)
`)
