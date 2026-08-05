@import com.averpo.erp.bank.domain.BankTransaction
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<BankTransaction> transfers
@param org.springframework.data.domain.Page<?> page = null
@param String filterQuery = ""
@param Map<UUID, String> accountNames
@param Map<UUID, String> accountCurrencies
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

@template.layout.main(helpKey = "page.transfers", title = msg.lookup("transfer.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("transfer.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/transfers/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (DEC-068): давр + статус + матн (контактсиз) --%>
    @template.shared.listFilter(action = "/transfers", from = from, to = to, q = q, msg = msg, extra = @`
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
    `)

    @if(transfers.isEmpty())
        <p class="text-current/60">${msg.localize("transfer.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[760px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bt.col.number")}</th>
                <th>${msg.localize("transfer.col.accounts")}</th>
                <th>${msg.localize("bt.col.date")}</th>
                <th class="text-right">${msg.localize("bt.col.total")}</th>
                <th>${msg.localize("bt.col.memo")}</th>
                <th>${msg.localize("bt.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(BankTransaction txn : transfers)
                <%-- QBO-003: транзфернинг ЎЗ view'ига --%>
                <tr data-href="/transfers/${txn.getId().toString()}">
                    <td><a href="/transfers/${txn.getId().toString()}">${txn.getTxnNumber()}</a></td>
                    <td>
                        ${accountNames.getOrDefault(txn.getBankAccountId(), "-")}
                        @if(txn.getCounterpartAccountId() != null)
                            → ${accountNames.getOrDefault(txn.getCounterpartAccountId(), "-")}
                        @endif
                    </td>
                    <td>${txn.getTxnDate().toString()}</td>
                    <td class="text-right whitespace-nowrap">
                        ${Fmt.money(txn.getTotal())} ${txn.getCurrency().getCode()}
                        <%-- UI-005: ФАҚАТ кросс-валютада манзил сумма ҳам -
                             бир хил валютада такрор шовқин бўларди --%>
                        @if(txn.getCounterpartAmount() != null
                                && !txn.getCurrency().getCode().equals(accountCurrencies.getOrDefault(txn.getCounterpartAccountId(), txn.getCurrency().getCode())))
                            → ${Fmt.money(txn.getCounterpartAmount())} ${accountCurrencies.getOrDefault(txn.getCounterpartAccountId(), "")}
                        @endif
                    </td>
                    <td>${txn.getMemo()}</td>
                    <td>
                        @template.shared.badge(value = txn.getStatus().name(), msg = msg)
                    </td>
                    <td class="text-center">
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/transfers/${txn.getId().toString()}">${msg.localize("common.view")}</a>
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    @template.shared.pagination(page = page, baseUrl = "/transfers", filterQuery = filterQuery, msg = msg)
`)
