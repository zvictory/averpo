@import com.averpo.erp.bank.domain.BankReconciliation
@import com.averpo.erp.bank.service.ReconciliationService
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.EntryStatus
@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@import java.util.List
@param BankReconciliation recon
@param String accountName
@param String accountCurrency
@param List<ReconciliationService.Candidate> candidates
@param BigDecimal difference
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = msg.lookup("rcn.view.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    !{var inProgress = recon.getStatus() == BankReconciliation.Status.IN_PROGRESS;}
    !{var editable = canEdit != null && canEdit && inProgress;}
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "reconciliation") ${msg.localize("rcn.view.title")}: ${accountName}</h1>
        <a href="/reconciliation" class="text-primary underline dark:text-primary-dark">${msg.localize("rcn.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- QBO Reconcile сарлавҳаси: қолдиқлар ва ЖОНЛИ ФАРҚ. Ҳамма сумма
         счёт валютасида (banking.md) - код ёнида кўрсатилади (UI-002) --%>
    <div class="mb-4 grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(170px,1fr))] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
        <div><b>${msg.localize("rcn.col.statementDate")}:</b> ${recon.getStatementDate().toString()}</div>
        <div><b>${msg.localize("rcn.col.opening")}:</b> ${Fmt.money(recon.getOpeningBalance())} ${accountCurrency}</div>
        <div><b>${msg.localize("rcn.col.closing")}:</b> ${Fmt.money(recon.getClosingBalance())} ${accountCurrency}</div>
        <div><b>${msg.localize("rcn.col.status")}:</b>
            @template.shared.badge(value = recon.getStatus().name(), family = "rcn.status", msg = msg)
        </div>
        <div><b>${msg.localize("rcn.view.difference")}:</b>
            @if(difference.signum() == 0)
                <span class="font-bold text-success">0</span>
            @else
                <span class="font-bold text-danger">${Fmt.money(difference)} ${accountCurrency}</span>
            @endif
        </div>
    </div>

    @if(inProgress)
        <p class="mb-4 text-current/60">${msg.localize("rcn.view.hint")}</p>
    @endif

    @if(candidates.isEmpty())
        <p class="text-current/60">${msg.localize("rcn.view.noLines")}</p>
    @else
        @template.shared.table(cls = "min-w-[820px]", wrap = "mb-5", c = @`
            <thead>
            <tr>
                <th class="w-12">${msg.localize("rcn.view.cleared")}</th>
                <th>${msg.localize("rcn.col.entryNumber")}</th>
                <th>${msg.localize("rcn.col.date")}</th>
                <th>${msg.localize("rcn.col.description")}</th>
                <%-- Валюта коди сарлавҳада - ҳар қаторда такрорлаш шарт эмас,
                     жадвал бир счётники (UI-002 арбитр кўлами) --%>
                <th class="text-right">${msg.localize("rcn.col.inflow")} (${accountCurrency})</th>
                <th class="text-right">${msg.localize("rcn.col.outflow")} (${accountCurrency})</th>
            </tr>
            </thead>
            <tbody>
            @for(ReconciliationService.Candidate candidate : candidates)
                !{var line = candidate.line();}
                <tr class="${candidate.matchedElsewhere() ? "opacity-45" : ""}">
                    <td class="text-center">
                        @if(candidate.matchedElsewhere())
                            <span title="${msg.lookup("rcn.view.lockedElsewhere")}">🔒</span>
                        @elseif(editable)
                            <%-- Checkbox auto-submit: ҳар босишда сервер фарқни
                                 қайта ҳисоблайди (CSRF билан оддий POST) --%>
                            <form method="post" action="/reconciliation/${recon.getId().toString()}/toggle" style="margin:0;">
                                @template.shared.csrf(csrf = csrf)
                                <input type="hidden" name="lineId" value="${line.lineId().toString()}">
                                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark size-[1.1rem] cursor-pointer accent-primary dark:accent-primary-dark" checked="${candidate.matchedHere()}"
                                       onchange="this.form.submit()"
                                      >
                            </form>
                        @elseif(candidate.matchedHere())
                            ✓
                        @endif
                    </td>
                    <td><a href="/journal-entries/${line.entryId().toString()}">${line.entryNumber()}</a>
                        @if(line.status() == EntryStatus.REVERSED)
                            <span class="text-[.8rem] text-current/50">(${msg.localize("status.REVERSED")})</span>
                        @endif
                    </td>
                    <td>${line.entryDate().toString()}</td>
                    <td>${line.description()}</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                        @if(line.signedAmount().signum() > 0)${Fmt.money(line.signedAmount())}@endif
                    </td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                        @if(line.signedAmount().signum() < 0)${Fmt.money(line.signedAmount().negate())}@endif
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    @if(editable)
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
            <form method="post" action="/reconciliation/${recon.getId().toString()}/complete">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("rcn.view.complete")}</button>
            </form>
            <form method="post" action="/reconciliation/${recon.getId().toString()}/cancel"
                  onsubmit="return confirm('${msg.lookup("rcn.view.cancelConfirm")}');">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-4 py-2 text-center text-sm font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("rcn.view.cancel")}</button>
            </form>
        </div>
    @endif
`)
