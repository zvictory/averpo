@import com.averpo.erp.bank.domain.BankTransaction
@import com.averpo.erp.bank.domain.BankTransactionType
@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<BankTransaction> transactions
@param Map<UUID, String> accountNames
@param List<Contact> contacts = null
@param org.springframework.data.domain.Page<BankTransaction> page = null
@param String filterQuery = ""
@param String from = ""
@param String to = ""
@param String status = ""
@param String contactId = ""
@param String q = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.bankTransactions", title = msg.lookup("bt.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("bt.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/bank-transactions/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + контакт + матн
         (матн txn рақами, банк ҳужжат ҳаваласи ref_no ва изоҳни қамрайди) --%>
    @template.shared.listFilter(action = "/bank-transactions", from = from, to = to, q = q, msg = msg, extra = @`
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
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.contact")}
            @template.shared.combobox(name = "contactId", msg = msg,
                    value = contactId,
                    placeholder = msg.lookup("filter.all"),
                    cls = "mt-1 w-56 max-w-56",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("filter.all"))
                        @if(contacts != null)
                            @for(Contact contact : contacts)
                                @template.shared.comboOption(value = contact.getId().toString(),
                                        label = contact.getDisplayName(),
                                        selected = contact.getId().toString().equals(contactId))
                            @endfor
                        @endif
                    `)
        </label>
    `)

    @if(transactions.isEmpty())
        <p class="text-current/60">${msg.localize("bt.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[860px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bt.col.number")}</th>
                <th>${msg.localize("bt.col.type")}</th>
                <th>${msg.localize("bt.col.bank")}</th>
                <th>${msg.localize("bt.col.date")}</th>
                <th class="text-right">${msg.localize("bt.col.total")}</th>
                <th>${msg.localize("bt.col.memo")}</th>
                <th>${msg.localize("bt.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(BankTransaction txn : transactions)
                <tr data-href="/bank-transactions/${txn.getId().toString()}">
                    <td><a href="/bank-transactions/${txn.getId().toString()}">${txn.getTxnNumber()}</a></td>
                    <td>${msg.localize("bt.type." + txn.getType().name())}</td>
                    <td>
                        ${accountNames.getOrDefault(txn.getBankAccountId(), "-")}
                        @if(txn.getType() == BankTransactionType.TRANSFER && txn.getCounterpartAccountId() != null)
                            → ${accountNames.getOrDefault(txn.getCounterpartAccountId(), "-")}
                        @endif
                    </td>
                    <td>${txn.getTxnDate().toString()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(txn.getTotal())} ${txn.getCurrency().getCode()}</td>
                    <td>${txn.getMemo()}</td>
                    <td>
                        @template.shared.badge(value = txn.getStatus().name(), msg = msg)
                    </td>
                    <td class="text-center">
                        <%-- Nargiza-007 P1a: Action менюси --%>
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/bank-transactions/${txn.getId().toString()}">${msg.localize("common.view")}</a>
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (Beruniy-perf1); филтрсиз рўйхат --%>
    @template.shared.pagination(page = page, baseUrl = "/bank-transactions", filterQuery = filterQuery, msg = msg)
`)
