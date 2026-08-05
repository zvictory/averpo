@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.EntryStatus
@import com.averpo.erp.ledger.service.AccountTransactionsService
@import com.averpo.erp.shared.web.Fmt
@import java.util.Map
@import java.util.UUID
@param AccountTransactionsService.Register register
@param Map<UUID, String> contactNames
@param String homeCurrency
@param org.springframework.data.domain.Page<AccountTransactionsService.Row> page = null
@param String filterQuery = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.accountTransactions", title = msg.lookup("acctTx.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-1.5 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("acctTx.title")}:
        @if(register.account().getCode() != null)${register.account().getCode()} - @endif${register.account().getName()}</h1>
    <%-- Дебет/кредит ҳужжат валютасида (Fmt.money кодини ёзади), қолдиқ эса home'да --%>
    <p class="mb-4 text-current/60">${msg.localize("acctTx.balanceNote", homeCurrency)}</p>

    <form method="get" action="/accounts/${register.account().getId().toString()}/transactions"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("entries.filter.from")}
            <input type="date" name="from" value="${register.from().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("entries.filter.to")}
            <input type="date" name="to" value="${register.to().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("entries.filter.apply")}</button>
    </form>

    @template.shared.table(cls = "min-w-[860px]", c = @`
        <thead>
        <tr>
            <th>${msg.localize("entries.col.date")}</th>
            <th>${msg.localize("entries.col.number")}</th>
            <th>${msg.localize("entries.col.memo")}</th>
            <th>${msg.localize("acctTx.col.contact")}</th>
            <th>${msg.localize("entries.col.status")}</th>
            <th class="text-right">${msg.localize("entries.col.debit")}</th>
            <th class="text-right">${msg.localize("entries.col.credit")}</th>
            <th class="text-right">${msg.localize("acctTx.col.balance")}</th>
        </tr>
        </thead>
        <tbody>
        <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
            <td colspan="7">${msg.localize("acctTx.opening")}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.opening())}</td>
        </tr>
        @for(AccountTransactionsService.Row row : register.rows())
            <%-- Ҳар қатор ўз JE'сига боради (T0 row-click механизми) --%>
            <tr data-href="/journal-entries/${row.entryId().toString()}">
                <td>${row.entryDate().toString()}</td>
                <td><a href="/journal-entries/${row.entryId().toString()}">${row.entryNumber()}</a></td>
                <td>${row.description()}</td>
                <td>@if(row.contactId() != null)${contactNames.getOrDefault(row.contactId(), "-")}@endif</td>
                <td>
                    @template.shared.badge(value = row.status().name(), msg = msg)
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.debit())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.credit())}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.balance())}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
            <td colspan="7">${msg.localize("acctTx.closing")}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(register.closing())}</td>
        </tr>
        </tfoot>
    `)
    @if(register.rows().isEmpty())
        <p class="mt-2 text-current/60">${msg.localize("tb.empty")}</p>
    @endif
    <%-- Саҳифалаш (ARBITR-105б): rows фақат жорий саҳифа, жорий қолдиқ
         саҳифалараро узлуксиз (service «саҳифагача йиғинди» aggregate'и);
         давр филтри линкларда сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/accounts/" + register.account().getId().toString() + "/transactions", filterQuery = filterQuery, msg = msg)
`)
