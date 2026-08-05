@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.sales.service.InvoiceService
@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@import java.time.LocalDate
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<InvoiceService.AgingRow> rows
@param Map<UUID, String> customerNames
@param LocalDate asOf
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.arAging", title = msg.lookup("araging.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("araging.title")}</h1>
    </div>

    <%-- BR-RPT-001 (IFRS-004): сана танлаш йўқ - ҳисобот фақат жорий
         ҳолатни кўрсатади, тарихий as-of реконструкцияси 9-босқичда --%>
    <p style="margin-bottom:.5rem;">${msg.localize("araging.asOf")}: <b>${asOf.toString()}</b> - ${msg.localize("report.currentOnly")}</p>

    <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}. ${msg.localize("araging.note")} @template.shared.help(key = "field.agingBuckets", msg = msg)</p>

    @if(rows.isEmpty())
        <p class="text-current/60">${msg.localize("araging.empty")}</p>
    @else
        !{var totals = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};}
        @template.shared.table(cls = "min-w-[860px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("araging.col.customer")}</th>
                <th class="text-right">${msg.localize("araging.col.current")}</th>
                <th class="text-right">1-30</th>
                <th class="text-right">31-60</th>
                <th class="text-right">61-90</th>
                <th class="text-right">90+</th>
                <th class="text-right">${msg.localize("araging.col.total")}</th>
            </tr>
            </thead>
            <tbody>
            @for(InvoiceService.AgingRow row : rows)
                !{totals[0] = totals[0].add(row.current());}
                !{totals[1] = totals[1].add(row.d1to30());}
                !{totals[2] = totals[2].add(row.d31to60());}
                !{totals[3] = totals[3].add(row.d61to90());}
                !{totals[4] = totals[4].add(row.over90());}
                !{totals[5] = totals[5].add(row.total());}
                <%-- T11 паттерни: қатор босилса шу мижознинг очиқ invoice'лари --%>
                <tr data-href="/invoices?customerId=${row.customerId().toString()}&open=true">
                    <td>${customerNames.getOrDefault(row.customerId(), "-")}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.current())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.d1to30())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.d31to60())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.d61to90())}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.over90())}</td>
                    <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(row.total())}</td>
                </tr>
            @endfor
            </tbody>
            <tfoot>
            <tr>
                <td class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("araging.col.total")}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(totals[0])}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(totals[1])}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(totals[2])}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(totals[3])}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(totals[4])}</td>
                <td class="text-right font-bold whitespace-nowrap tabular-nums">${Fmt.money(totals[5])}</td>
            </tr>
            </tfoot>
        `)
    @endif
`)
