@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.service.ProfitAndLossService
@import com.averpo.erp.shared.web.Fmt
@param ProfitAndLossService.Report report
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.profitLoss", title = msg.lookup("pl.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-1.5 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("pl.heading")}</h1>
    <%-- U3: QBO услуби - катакларга код ёзилмайди, тепада битта ёзув --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>

    <form method="get" action="/reports/profit-loss"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.from")}
            <input type="date" name="from" value="${report.from().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.to")}
            <input type="date" name="to" value="${report.to().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("preset.quick")}
            <select id="plPreset" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="">${msg.localize("preset.choose")}</option>
                <option value="thisMonth">${msg.localize("preset.thisMonth")}</option>
                <option value="thisQuarter">${msg.localize("preset.thisQuarter")}</option>
                <option value="thisYear">${msg.localize("preset.thisYear")}</option>
                <option value="lastMonth">${msg.localize("preset.lastMonth")}</option>
                <option value="lastQuarter">${msg.localize("preset.lastQuarter")}</option>
                <option value="lastYear">${msg.localize("preset.lastYear")}</option>
            </select>
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tb.show")}</button>
    </form>

        @template.shared.table(cls = "min-w-[520px]", wrap = "max-w-[760px]", c = @`
            <tbody>
            @if(!report.income().rows().isEmpty())
                <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("pl.income")}</td></tr>
                @for(ProfitAndLossService.Row r : report.income().rows())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${report.from().toString()}&to=${report.to().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("pl.totalIncome")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.income().total())}</td>
                </tr>
            @endif

            @if(!report.cogs().rows().isEmpty())
                <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("pl.cogs")}</td></tr>
                @for(ProfitAndLossService.Row r : report.cogs().rows())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${report.from().toString()}&to=${report.to().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("pl.totalCogs")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.cogs().total())}</td>
                </tr>
            @endif

            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td>${msg.localize("pl.grossProfit")}</td>
                <td class="border-t-2 border-outline text-right dark:border-outline-dark">${Fmt.money(report.grossProfit())}</td>
            </tr>

            @if(!report.expenses().rows().isEmpty())
                <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("pl.expenses")}</td></tr>
                @for(ProfitAndLossService.Row r : report.expenses().rows())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${report.from().toString()}&to=${report.to().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("pl.totalExpenses")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.expenses().total())}</td>
                </tr>
            @endif

            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td>${msg.localize("pl.operatingIncome")}</td>
                <td class="border-t-2 border-outline text-right dark:border-outline-dark">${Fmt.money(report.operatingIncome())}</td>
            </tr>

            @if(!report.otherIncome().rows().isEmpty())
                <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("pl.otherIncome")}</td></tr>
                @for(ProfitAndLossService.Row r : report.otherIncome().rows())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${report.from().toString()}&to=${report.to().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("pl.totalOtherIncome")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.otherIncome().total())}</td>
                </tr>
            @endif

            @if(!report.otherExpenses().rows().isEmpty())
                <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("pl.otherExpenses")}</td></tr>
                @for(ProfitAndLossService.Row r : report.otherExpenses().rows())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${report.from().toString()}&to=${report.to().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("pl.totalOtherExpenses")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.otherExpenses().total())}</td>
                </tr>
            @endif

            @if(!report.otherIncome().rows().isEmpty() || !report.otherExpenses().rows().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td>${msg.localize("pl.netOtherIncome")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.netOtherIncome())}</td>
                </tr>
            @endif

            <%-- Komil-010 (IAS 1.82(b)): солиқ харажати алоҳида бўлим, тартиб:
                 Солиққача фойда → Солиқ харажати → Соф фойда. Оралиқ сатрлар
                 фақат солиқ айланмаси бор даврда кўрсатилади. --%>
            @if(!report.taxExpense().rows().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td>${msg.localize("pl.profitBeforeTax")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.profitBeforeTax())}</td>
                </tr>
                <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("pl.taxExpense")}</td></tr>
                @for(ProfitAndLossService.Row r : report.taxExpense().rows())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${report.from().toString()}&to=${report.to().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("pl.totalTaxExpense")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.taxExpense().total())}</td>
                </tr>
            @endif

            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td>${msg.localize("pl.netIncome")}</td>
                <td class="border-t-2 border-outline text-right dark:border-outline-dark">${Fmt.money(report.netIncome())}</td>
            </tr>
            </tbody>
    `)

    <script>
        // Тез давр танлови: from/to инпутларини тўлдириб формани юборади.
        // Жорий даврлар тўлиқ календар даври (QBO услуби), охири келажакда
        // бўлса ҳам - келажак санада маълумот бўлмайди.
        document.getElementById('plPreset').addEventListener('change', function () {
            if (!this.value) return;
            var now = new Date(), y = now.getFullYear(), m = now.getMonth();
            var q = Math.floor(m / 3) * 3, from, to;
            if (this.value === 'thisMonth') { from = new Date(y, m, 1); to = new Date(y, m + 1, 0); }
            if (this.value === 'thisQuarter') { from = new Date(y, q, 1); to = new Date(y, q + 3, 0); }
            if (this.value === 'thisYear') { from = new Date(y, 0, 1); to = new Date(y, 12, 0); }
            if (this.value === 'lastMonth') { from = new Date(y, m - 1, 1); to = new Date(y, m, 0); }
            if (this.value === 'lastQuarter') { from = new Date(y, q - 3, 1); to = new Date(y, q, 0); }
            if (this.value === 'lastYear') { from = new Date(y - 1, 0, 1); to = new Date(y, 0, 0); }
            var iso = function (d) {
                return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
                        + '-' + String(d.getDate()).padStart(2, '0');
            };
            var form = this.closest('form');
            form.querySelector('input[name=from]').value = iso(from);
            form.querySelector('input[name=to]').value = iso(to);
            form.submit();
        });
    </script>
`)
