@import com.averpo.erp.bank.domain.BankReconciliation
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<BankReconciliation> reconciliations
@param org.springframework.data.domain.Page<?> page = null
@param Map<UUID, String> accountNames
@param Map<UUID, String> accountCurrencies
@param String homeCurrency
@param List<Account> bankAccounts
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.reconciliations", title = msg.lookup("rcn.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("rcn.title")}</h1>
    </div>

    @template.shared.alert(message = message, error = error)

    @if(canEdit != null && canEdit)
        <%-- QBO Reconcile бошлаш формаси: счёт + кўчирма санаси + қолдиқлар --%>
        <form method="post" action="/reconciliation/start"
              class="mb-5 flex flex-wrap items-end gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("rcn.form.account")}
                <%-- Валюта коди ёрлиқда - қолдиқ майдонлари қайси валютада
                     киритилишини танлашдаёқ кўрсатади (UI-002) --%>
                @template.shared.combobox(name = "accountId", msg = msg,
                        value = null,
                        placeholder = msg.lookup("bt.form.selectBank"),
                        required = true,
                        cls = "mt-1 min-w-[180px]",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("bt.form.selectBank"))
                            @for(Account account : bankAccounts)
                                @template.shared.comboOption(value = account.getId().toString(),
                                        label = account.getName() + " (" + (account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency) + ")")
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("rcn.form.statementDate")}
                <input type="date" name="statementDate" required
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark"><span class="inline-flex items-center gap-1">${msg.localize("rcn.form.closingBalance")} @template.shared.help(key = "field.reconBalance", msg = msg)</span>
                <input name="closingBalance" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" required size="14"
                      >
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("rcn.form.openingBalance")}
                <input name="openingBalance" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" size="14"
                      >
                <small class="block text-current/60">${msg.localize("rcn.form.openingNote")}</small>
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("rcn.form.start")}</button>
        </form>
    @endif

    @if(reconciliations.isEmpty())
        <p class="text-current/60">${msg.localize("rcn.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[760px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("rcn.col.account")}</th>
                <th>${msg.localize("rcn.col.statementDate")}</th>
                <th class="text-right">${msg.localize("rcn.col.opening")}</th>
                <th class="text-right">${msg.localize("rcn.col.closing")}</th>
                <th>${msg.localize("rcn.col.status")}</th>
            </tr>
            </thead>
            <tbody>
            @for(BankReconciliation recon : reconciliations)
                <tr data-href="/reconciliation/${recon.getId().toString()}">
                    <td><a href="/reconciliation/${recon.getId().toString()}">${accountNames.getOrDefault(recon.getAccountId(), "-")}</a></td>
                    <td>${recon.getStatementDate().toString()}</td>
                    <%-- Қолдиқлар счёт валютасида (banking.md) - код ёнида,
                         UZS/USD счётлар аралаш рўйхатда адашмаслик учун --%>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(recon.getOpeningBalance())} ${accountCurrencies.getOrDefault(recon.getAccountId(), homeCurrency)}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(recon.getClosingBalance())} ${accountCurrencies.getOrDefault(recon.getAccountId(), homeCurrency)}</td>
                    <td>
                        @template.shared.badge(value = recon.getStatus().name(), family = "rcn.status", msg = msg)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    @template.shared.pagination(page = page, baseUrl = "/reconciliation", msg = msg)
`)
