@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.payroll.domain.PayrollRun
@import com.averpo.erp.payroll.service.PayrollRunService.RunTotals
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<PayrollRun> runs
@param Map<UUID, RunTotals> totals = java.util.Map.of()
@param org.springframework.data.domain.Page<PayrollRun> page = null
@param String filterQuery = ""
@param String from = ""
@param String to = ""
@param String status = ""
@param String q = ""
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.payrollRuns", title = msg.lookup("prun.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("prun.title")}</h1>
        @if(canEdit != null && canEdit)
            @template.shared.createButton(msg = msg, href = "/payroll/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + матн --%>
    @template.shared.listFilter(action = "/payroll", from = from, to = to, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(PayrollRun.Status st : PayrollRun.Status.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize("status." + st.name())}
                    </option>
                @endfor
            </select>
        </label>
    `)

    @if(runs.isEmpty())
        <p class="text-current/60">${msg.localize("prun.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[720px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bt.col.number")}</th>
                <th>${msg.localize("prun.col.period")}</th>
                <th>${msg.localize("prun.col.runDate")}</th>
                <th class="text-right">${msg.localize("prun.col.gross")}</th>
                <th class="text-right">${msg.localize("prun.col.net")}</th>
                <th>${msg.localize("bt.col.status")}</th>
            </tr>
            </thead>
            <tbody>
            @for(PayrollRun run : runs)
                <tr data-href="/payroll/${run.getId().toString()}">
                    <td><a href="/payroll/${run.getId().toString()}">${run.getRunNumber()}</a></td>
                    <td>${run.getPeriod()}</td>
                    <td>${run.getRunDate().toString()}</td>
                    <%-- Жами gross/net - controller'дан JPQL агрегат харитаси
                         (run.totalGross() lazy lines'ни айланарди - Arbitr-054) --%>
                    <td class="text-right whitespace-nowrap">${Fmt.money(totals.getOrDefault(run.getId(), RunTotals.ZERO).gross())} ${homeCurrency}</td>
                    <td class="text-right whitespace-nowrap">${Fmt.money(totals.getOrDefault(run.getId(), RunTotals.ZERO).net())} ${homeCurrency}</td>
                    <td>
                        @template.shared.badge(value = run.getStatus().name(), msg = msg)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш - рўйхат саҳифаланган туғилади (Beruniy-perf1 қолипи) --%>
    @template.shared.pagination(page = page, baseUrl = "/payroll", filterQuery = filterQuery, msg = msg)
`)
