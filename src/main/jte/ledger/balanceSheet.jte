@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.service.BalanceSheetService
@import com.averpo.erp.shared.web.Fmt
@import java.time.LocalDate
@param BalanceSheetService.Report report
@param LocalDate fyStart
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.balanceSheet", title = msg.lookup("bs.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-1.5 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("bs.heading")}</h1>
    <%-- U3: QBO услуби - катакларга код ёзилмайди, тепада битта ёзув --%>
    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>

    <form method="get" action="/reports/balance-sheet"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("bs.asOf")}
            <input type="date" name="asOf" value="${report.asOf().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("preset.quick")}
            <select id="bsPreset" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="">${msg.localize("preset.choose")}</option>
                <option value="today">${msg.localize("preset.today")}</option>
                <option value="lastMonthEnd">${msg.localize("preset.lastMonthEnd")}</option>
                <option value="lastQuarterEnd">${msg.localize("preset.lastQuarterEnd")}</option>
                <option value="lastYearEnd">${msg.localize("preset.lastYearEnd")}</option>
            </select>
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tb.show")}</button>
    </form>

    @if(!report.balanced())
        <p class="mb-4 rounded-radius border border-danger/30 bg-danger/10 px-3 py-2.5 text-danger">
            ${msg.localize("bs.notBalanced")}
        </p>
    @endif

        @template.shared.table(cls = "min-w-[520px]", wrap = "max-w-[760px]", c = @`
            <tbody>
            <%-- ======================= АКТИВЛАР ======================= --%>
            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("bs.assets")}</td></tr>

            @if(!report.currentAssetGroups().isEmpty() || report.vendorPrepayments().signum() != 0 || report.employeeAdvances().signum() != 0)
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("bs.currentAssets")}</td></tr>
                @for(BalanceSheetService.Group g : report.currentAssetGroups())
                    <tr><td colspan="2" class="pl-7!">${msg.localize(g.titleKey())}</td></tr>
                    @for(BalanceSheetService.Row r : g.rows())
                        <%-- Drill-down: жорий молия йили давридаги счёт амаллари (T2) --%>
                        <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                            <td style="padding-left:2.5rem;">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                        </tr>
                    @endfor
                    <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                        <td class="pl-7!">${msg.localize("bs.groupTotal", msg.lookup(g.titleKey()))}</td>
                        <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(g.total())}</td>
                    </tr>
                @endfor
                @if(report.vendorPrepayments().signum() != 0)
                    <%-- IFRS-005: таъминотчиларга тақсимланмаган аванслар - AP дебет
                         қолдиғидан prepayment активга КЎРСАТИШДА reclass (IAS 1.32) --%>
                    <tr>
                        <td class="pl-7!">${msg.localize("bs.vendorPrepayments")}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(report.vendorPrepayments())}</td>
                    </tr>
                @endif
                @if(report.employeeAdvances().signum() != 0)
                    <%-- IFRS-020 (DEC-071): ходимларга берилган аванслар -
                         PAYROLL_CLEARING нетто дебетидан активга КЎРСАТИШДА
                         reclass (IAS 1.32), GL ўзгармайди --%>
                    <tr>
                        <td class="pl-7!">${msg.localize("bs.employeeAdvances")}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(report.employeeAdvances())}</td>
                    </tr>
                @endif
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.totalCurrentAssets")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalCurrentAssets())}</td>
                </tr>
            @endif

            @if(!report.fixedAssets().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("bs.fixedAssets")}</td></tr>
                @for(BalanceSheetService.Row r : report.fixedAssets())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.totalFixedAssets")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalFixedAssets())}</td>
                </tr>
            @endif

            <%-- IFRS-012: гудвилл ва номоддий активлар алоҳида моддалар
                 (IAS 1.54(c)) - «Бошқа активлар»дан ажратилган, GL ўзгармаган --%>
            @if(!report.goodwill().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("account.detail.GOODWILL")}</td></tr>
                @for(BalanceSheetService.Row r : report.goodwill())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.groupTotal", msg.lookup("account.detail.GOODWILL"))}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalGoodwill())}</td>
                </tr>
            @endif

            @if(!report.intangibleAssets().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("account.detail.INTANGIBLE_ASSETS")}</td></tr>
                @for(BalanceSheetService.Row r : report.intangibleAssets())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.groupTotal", msg.lookup("account.detail.INTANGIBLE_ASSETS"))}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalIntangibleAssets())}</td>
                </tr>
            @endif

            @if(!report.otherAssets().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("bs.otherAssets")}</td></tr>
                @for(BalanceSheetService.Row r : report.otherAssets())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.totalOtherAssets")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalOtherAssets())}</td>
                </tr>
            @endif

            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td>${msg.localize("bs.totalAssets")}</td>
                <td class="border-t-2 border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalAssets())}</td>
            </tr>

            <%-- ================ МАЖБУРИЯТ ВА КАПИТАЛ ================ --%>
            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong"><td colspan="2">${msg.localize("bs.liabEquity")}</td></tr>

            @if(!report.currentLiabilityGroups().isEmpty() || report.customerAdvances().signum() != 0)
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("bs.currentLiabilities")}</td></tr>
                @for(BalanceSheetService.Group g : report.currentLiabilityGroups())
                    <tr><td colspan="2" class="pl-7!">${msg.localize(g.titleKey())}</td></tr>
                    @for(BalanceSheetService.Row r : g.rows())
                        <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                            <td style="padding-left:2.5rem;">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                        </tr>
                    @endfor
                    <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                        <td class="pl-7!">${msg.localize("bs.groupTotal", msg.lookup(g.titleKey()))}</td>
                        <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(g.total())}</td>
                    </tr>
                @endfor
                @if(report.customerAdvances().signum() != 0)
                    <%-- IFRS-005: мижозлардан тақсимланмаган аванслар - AR кредит
                         қолдиғидан мажбуриятга КЎРСАТИШДА reclass (IFRS 15.106) --%>
                    <tr>
                        <td class="pl-7!">${msg.localize("bs.customerAdvances")}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(report.customerAdvances())}</td>
                    </tr>
                @endif
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.totalCurrentLiabilities")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalCurrentLiabilities())}</td>
                </tr>
            @endif

            @if(!report.longTermLiabilities().isEmpty())
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("bs.longTermLiabilities")}</td></tr>
                @for(BalanceSheetService.Row r : report.longTermLiabilities())
                    <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                        <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                    </tr>
                @endfor
                <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                    <td class="pl-4!">${msg.localize("bs.totalLongTermLiabilities")}</td>
                    <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalLongTermLiabilities())}</td>
                </tr>
            @endif

            <tr class="font-bold text-on-surface-strong dark:text-on-surface-dark-strong">
                <td>${msg.localize("bs.totalLiabilities")}</td>
                <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalLiabilities())}</td>
            </tr>

            <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong"><td colspan="2" class="pl-4!">${msg.localize("bs.equity")}</td></tr>
            @for(BalanceSheetService.Row r : report.equityRows())
                <tr data-href="/accounts/${r.accountId().toString()}/transactions?from=${fyStart.toString()}&to=${report.asOf().toString()}">
                    <td class="pl-7!">@if(r.code() != null)${r.code()} - @endif${r.name()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.amount())}</td>
                </tr>
            @endfor
            <%-- Ҳисобланган сатрлар (QBO услуби): RE = счёт + олдинги йиллар, NI = жорий молия йили --%>
            <tr>
                <td class="pl-7!">${msg.localize("bs.retainedEarnings")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(report.retainedEarnings())}</td>
            </tr>
            <tr>
                <td class="pl-7!">${msg.localize("bs.netIncome")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(report.netIncome())}</td>
            </tr>
            <tr class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">
                <td class="pl-4!">${msg.localize("bs.totalEquity")}</td>
                <td class="border-t border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalEquity())}</td>
            </tr>

            <tr class="bg-surface-alt font-bold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td>${msg.localize("bs.totalLiabEquity")}</td>
                <td class="border-t-2 border-outline text-right dark:border-outline-dark">${Fmt.money(report.totalLiabilitiesAndEquity())}</td>
            </tr>
            </tbody>
    `)

    <script>
        // Тез давр танлови: сана инпутини тўлдириб формани юборади.
        // Ҳисоб клиент вақтида - фақат қулайлик, аниқ сана инпутда кўринади.
        document.getElementById('bsPreset').addEventListener('change', function () {
            if (!this.value) return;
            var now = new Date(), d = now;
            if (this.value === 'lastMonthEnd') d = new Date(now.getFullYear(), now.getMonth(), 0);
            if (this.value === 'lastQuarterEnd') d = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 0);
            if (this.value === 'lastYearEnd') d = new Date(now.getFullYear(), 0, 0);
            var iso = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
                    + '-' + String(d.getDate()).padStart(2, '0');
            var form = this.closest('form');
            form.querySelector('input[name=asOf]').value = iso;
            form.submit();
        });
    </script>
`)
