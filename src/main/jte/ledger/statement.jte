@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.service.StatementService
@import com.averpo.erp.ledger.web.SourceDocLinks
@import com.averpo.erp.shared.web.Fmt
@import java.time.LocalDate
@import java.util.List
@param List<ContactRef> customers
@param String selectedCustomerId = ""
@param StatementService.Statement statement = null
@param String customerName = null
@param LocalDate from
@param LocalDate to
@param String homeCurrency
@param String companyName
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.statement", title = msg.lookup("statement.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <%-- Print полиши (Arbitr-053 банд 4 / 014): жадвал min-width:720px A4
         (≈688px)га сиғмайди - чоп этишда 0 + компакт padding (қатор
         чекланмайди). Локал <style> ўрнига print: утилиталар фрагмент
         cls/wrap'ида (Arbitr-120); padding'да ! - канон [&_td]:px-3
         дан устун бўлиши кафолати учун. --%>
    <%-- Форма/сарлавҳа/тугма - чоп этишда яширин (print:hidden) --%>
    <div class="print:hidden">
        <div class="mb-1.5 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("statement.heading")}</h1>
            @if(statement != null)
                <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" onclick="window.print()">${msg.localize("statement.print")}</button>
            @endif
        </div>
        <p class="mb-4 text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)} @template.shared.help(key = "field.allAmountsHome", msg = msg)</p>

        <form method="get" action="/reports/statement" class="mb-4 flex flex-wrap items-end gap-3">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("statement.customer")}
                @template.shared.combobox(name = "customerId", msg = msg,
                        value = selectedCustomerId,
                        placeholder = msg.lookup("statement.selectCustomer"),
                        cls = "mt-1 min-w-[220px]",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("statement.selectCustomer"))
                            @for(ContactRef c : customers)
                                @template.shared.comboOption(value = c.id().toString(),
                                        label = c.displayName(),
                                        selected = c.id().toString().equals(selectedCustomerId))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.from")}
                <input type="date" name="from" value="${from.toString()}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.to")}
                <input type="date" name="to" value="${to.toString()}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tb.show")}</button>
        </form>
    </div>

    @if(statement == null)
        <p class="text-current/60">${msg.localize("statement.chooseHint")}</p>
    @else
        <%-- Print сарлавҳа: фақат чоп этишда (hidden print:block) - компания +
             мижоз + давр (экранда сарлавҳа қуйида, print:hidden) --%>
        <div class="mb-4 hidden print:block">
            <h2 class="mb-1 text-xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${companyName}</h2>
            <div>${msg.localize("statement.heading")}: <b>${customerName}</b></div>
            <div>${from.toString()} - ${to.toString()}</div>
            <%-- 043: «Барча суммалар <home> да» экранда print:hidden блокда - чоп
                 этишда валюта контексти йўқолмасин деб print сарлавҳада ҳам --%>
            <div class="text-sm text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}</div>
        </div>
        <h2 class="mb-2 text-xl font-bold text-on-surface-strong print:hidden dark:text-on-surface-dark-strong">${customerName}
            <span class="text-[.9rem] font-normal text-current/60">(${from.toString()} - ${to.toString()})</span></h2>

        @template.shared.table(cls = "min-w-[720px] print:min-w-0 print:[&_th]:p-[2px_5px]! print:[&_td]:p-[2px_5px]! print:[&_th]:text-[11px] print:[&_td]:text-[11px]", wrap = "print:overflow-visible print:rounded-none print:border-0", c = @`
            <thead>
            <tr>
                <th>${msg.localize("statement.col.date")}</th>
                <th>${msg.localize("statement.col.type")}</th>
                <th>${msg.localize("statement.col.doc")}</th>
                <th class="text-right">${msg.localize("statement.col.charge")}</th>
                <th class="text-right">${msg.localize("statement.col.payment")}</th>
                <th class="text-right">${msg.localize("statement.col.balance")} @template.shared.help(key = "field.runningBalance", msg = msg)</th>
            </tr>
            </thead>
            <tbody>
            <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
                <td colspan="5">${msg.localize("statement.opening")}</td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(statement.opening())}</td>
            </tr>
            @for(StatementService.Row r : statement.rows())
                <tr>
                    <td class="whitespace-nowrap">${r.date().toString()}</td>
                    <td>@if(r.sourceModule() == null)${msg.localize("statement.type.MANUAL")}@else${msg.localize("statement.type." + r.sourceModule())}@endif</td>
                    <%-- Arbitr-063: рақам линк - ҳужжатга (тур маълум бўлса),
                         бўлмаса JE'га; print'да линк оддий матндек чиқаверади --%>
                    !{var docUrl = SourceDocLinks.url(r.sourceModule(), r.documentId());}
                    <td>
                        @if(r.documentNumber() != null && docUrl != null)
                            <a href="${docUrl}" class="font-medium text-primary no-underline hover:underline dark:text-primary-dark">${r.documentNumber()}</a>
                        @elseif(r.documentNumber() != null)
                            ${r.documentNumber()}
                        @elseif(r.entryId() != null)
                            <a href="/journal-entries/${r.entryId().toString()}" class="font-medium text-primary no-underline hover:underline dark:text-primary-dark">${r.entryNumber()}</a>
                        @else
                            ${r.entryNumber()}
                        @endif
                    </td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">@if(r.amount().signum() > 0)${Fmt.money(r.amount())}@endif</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">@if(r.amount().signum() < 0)${Fmt.money(r.amount().negate())}@endif</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(r.balance())}</td>
                </tr>
            @endfor
            @if(statement.rows().isEmpty())
                <tr><td colspan="6" class="text-current/60">${msg.localize("statement.noActivity")}</td></tr>
            @endif
            </tbody>
            <tfoot>
            <tr>
                <td colspan="5">${msg.localize("statement.closing")}</td>
                <td class="whitespace-nowrap text-right tabular-nums">${Fmt.money(statement.closing())}</td>
            </tr>
            </tfoot>
        `)
    @endif
`)
