@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.payroll.web.PayrollPaymentForm
@import java.util.List
@param PayrollPaymentForm form
@param List<Account> bankAccounts
@param List<Contact> employees
@param java.util.Map<String, String> bankBalances
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param Boolean canEdit = false

<%-- Иш ҳақи тўлови формаси (payroll.md 23в): тур (аванс/ойлик) | тўлов
     счёти (жонли Balance) | сана | сатрлар (ходим + сумма). «Очиқ
     қолдиқни тўлдириш» - HTMX prefill (unpaidByEmployee, tbody алмашади).
     Жами сатр ЖОНЛИ (қуйидаги скрипт, data-amount). Home валютада. --%>
@template.layout.form(title = msg.lookup("payp.form.title"), closeHref = "/payroll/payments", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="paypForm" method="post" action="/payroll/payments"
          x-data="{bal: ''}"
          x-init="bal = document.querySelector('input[name=accountId]')?.dataset.balance || ''">
        @template.shared.csrf(csrf = csrf)
        @if(form.getId() != null && !form.getId().isBlank())
            <input type="hidden" name="id" value="${form.getId()}">
        @endif

        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("payp.form.type")}
                <select name="paymentType"
                        class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="SALARY" selected="${"SALARY".equals(form.getPaymentType())}">${msg.localize("payp.type.SALARY")}</option>
                    <option value="ADVANCE" selected="${"ADVANCE".equals(form.getPaymentType())}">${msg.localize("payp.type.ADVANCE")}</option>
                </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("payp.form.account")}
                <%-- Банк қолдиғи value input data-balance'ида - Alpine bal
                     занжири ўша ерни ўқийди --%>
                !{var selAcc = bankAccounts.stream().filter(a -> a.getId().toString().equals(form.getAccountId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "accountId", msg = msg,
                        value = form.getAccountId(),
                        placeholder = msg.lookup("bt.form.selectBank"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                        dataBalance = selAcc != null ? bankBalances.get(selAcc.getId().toString()) : null,
                        onchange = "bal = $event.target.dataset.balance || ''",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("bt.form.selectBank"))
                            @for(Account account : bankAccounts)
                                @template.shared.comboOption(value = account.getId().toString(),
                                        label = (account.getCode() != null ? account.getCode() + " " : "") + account.getName() + " - " + (account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency),
                                        selected = account.getId().toString().equals(form.getAccountId()),
                                        dataBalance = bankBalances.get(account.getId().toString()))
                            @endfor
                        `)
                <span x-show="bal" x-cloak class="text-sm text-current/60">
                    ${msg.localize("bt.form.balance")}: <b x-text="bal"></b></span>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bt.col.date")}
                <input type="date" name="paymentDate" required
                       value="${form.getPaymentDate() != null ? form.getPaymentDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("sinv.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <p class="mb-2 text-sm text-current/60">${msg.localize("payp.form.amountsIn", homeCurrency)}</p>
        @template.shared.table(cls = "min-w-[520px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th class="w-10">#</th>
                <th class="min-w-[200px]">${msg.localize("prun.row.employee")}</th>
                <th class="text-right">${msg.localize("payp.col.amount")} <span class="font-normal">${homeCurrency}</span></th>
                <th class="w-10"></th>
            </tr>
            </thead>
            <tbody id="lineRows">
            @for(var i = 0; i < form.getLines().size(); i++)
                @template.payroll.payrollPaymentLineRow(index = i, employees = employees,
                        msg = msg, line = form.getLines().get(i))
            @endfor
            </tbody>
            <tfoot>
            <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td colspan="2" class="text-right">${msg.localize("prun.form.total")}:</td>
                <td class="text-right" data-total-amount></td>
                <td></td>
            </tr>
            </tfoot>
        `)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;margin-bottom:1rem;">
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-info bg-info px-4 py-2 text-center text-sm font-medium tracking-wide text-on-info transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75"
                    hx-get="/payroll/payments/prefill-rows"
                    hx-target="#lineRows" hx-swap="innerHTML">${msg.localize("payp.form.prefill")}</button>
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
                    hx-get="/payroll/payments/line-row"
                    hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr.line")].map(r => 1 + Number(r.dataset.index || 0)))}'
                    hx-target="#lineRows" hx-swap="beforeend">${msg.localize("bt.form.addRow")}</button>
        </div>
    </form>

    <script>
        (function () {
            function fmt(v) {
                // Минг ажратгич NBSP (Arbitr-053 банд 8) - Fmt.money/
                // money-input.js мослиги (қоида: минг ажратгич NBSP)
                return v.toLocaleString("en-US", {minimumFractionDigits: 2, maximumFractionDigits: 2})
                        .replace(/,/g, " ");
            }
            function recalc() {
                var total = 0;
                document.querySelectorAll("#lineRows tr.line [data-amount]").forEach(function (inp) {
                    var raw = (inp.value || "").replace(/[\s ]/g, "").replace(",", ".");
                    var v = Number(raw);
                    if (isFinite(v) && v > 0) total += v;
                });
                var cell = document.querySelector("[data-total-amount]");
                if (cell) cell.textContent = total > 0 ? fmt(total) : "";
            }
            document.addEventListener("input", function (e) {
                if (e.target && e.target.hasAttribute("data-amount")) recalc();
            });
            document.addEventListener("payroll-pay-recalc", recalc);
            document.body.addEventListener("htmx:afterSwap", recalc);
            recalc();
        })();
    </script>
`, actions = @`
    <a href="/payroll/payments" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" type="submit" form="paypForm" name="action" value="draft">${msg.localize("prun.form.saveDraft")}</button>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="paypForm" name="action" value="post">
            ${msg.localize("payp.form.savePost")}</button>
    </div>
`)
