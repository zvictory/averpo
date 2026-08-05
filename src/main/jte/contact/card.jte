@import com.averpo.erp.contact.web.ContactCardController.ContactCard
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.service.StatementService
@import com.averpo.erp.ledger.web.SourceDocLinks
@import com.averpo.erp.shared.web.Fmt
@param ContactCard card
@param String homeCurrency
@param String companyName
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.contactCard", title = card.displayName(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <%-- Кўчирма жадвалининг print полиши фрагмент cls/wrap
         утилиталарида (DEC-120, shared/table.jte чақируви);
         бутун экран Penguin утилиталарда (DEC-122). --%>
    <%-- Сарлавҳа: ном + тури + Таҳрир (эски форма) + Печат кўчирма --%>
    <div class="mb-4 flex flex-wrap items-start justify-between gap-2 print:hidden">
        <div>
            <h1 class="mb-0.5 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${card.displayName()}</h1>
            <div class="text-[.9rem] text-current/60">
                ${card.customer() ? msg.localize("contactcard.type.customer") : msg.localize("contactcard.type.vendor")}
                @if(card.contactCompany() != null) · ${card.contactCompany()}@endif
            </div>
        </div>
        <div class="flex flex-wrap gap-2">
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" onclick="window.print()">${msg.localize("contactcard.printStatement")}</button>
            @if(canEdit != null && canEdit)
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/${card.kind()}/${card.id().toString()}/edit">${msg.localize("contactcard.edit")}</a>
            @endif
        </div>
    </div>

    <%-- 4 стат-карта: жорий қолдиқ (аванс белгиси) / муддати ўтган /
         жами сотув(харид) / охирги тўлов. Пул валюта коди билан. --%>
    !{var ccCard = "rounded-radius border border-outline bg-surface px-4 py-3.5 dark:border-outline-dark dark:bg-surface-dark";}
    !{var ccLabel = "mb-1.5 text-[.8rem] text-current/60";}
    !{var ccValue = "text-[1.2rem] font-bold text-on-surface-strong dark:text-on-surface-dark-strong";}
    <%-- Стат-карталар: десктоп 4 устун, мобил 375px 2×2 (spec) --%>
    <div class="mb-6 grid grid-cols-2 gap-4 min-[641px]:grid-cols-4 print:hidden">
        <div class="${ccCard}">
            <div class="${ccLabel}">${msg.localize("contactcard.stat.balance")}</div>
            <div class="${ccValue}">${Fmt.money(card.balance().abs())} ${homeCurrency}@if(card.advance())<span class="ml-1 inline-block rounded bg-success/15 px-1.5 py-0.5 align-middle text-[.7rem] font-semibold text-success">${msg.localize("contactcard.stat.advance")}</span>@endif</div>
        </div>
        <div class="${ccCard}">
            <div class="${ccLabel}">${msg.localize("contactcard.stat.overdue")}</div>
            <div class="text-[1.2rem] font-bold ${card.overdue().signum() > 0 ? "text-danger" : "text-on-surface-strong dark:text-on-surface-dark-strong"}">${Fmt.money(card.overdue())} ${homeCurrency}</div>
        </div>
        <div class="${ccCard}">
            <div class="${ccLabel}">${card.customer() ? msg.localize("contactcard.stat.totalSales") : msg.localize("contactcard.stat.totalPurchases")}</div>
            <div class="${ccValue}">${Fmt.money(card.total())} ${homeCurrency}</div>
        </div>
        <div class="${ccCard}">
            <div class="${ccLabel}">${msg.localize("contactcard.stat.lastPayment")}</div>
            <div class="${ccValue}">@if(card.lastPayment() != null)${card.lastPayment().toString()}@else<span class="text-current/50">-</span>@endif</div>
        </div>
    </div>

    <%-- Кўчирма бўлими: давр филтри + running balance жадвали
         (statement.jte service реюз - statement.jte'нинг ЎЗИГА тегилмайди) --%>
    <h2 class="mb-2 text-xl font-bold text-on-surface-strong print:hidden dark:text-on-surface-dark-strong">${msg.localize("contactcard.statement")}</h2>
    <p class="mb-3 text-current/60 print:hidden">${msg.localize("report.allAmountsIn", homeCurrency)}</p>

    <form class="mb-4 flex flex-wrap items-end gap-3 print:hidden" method="get" action="/${card.kind()}/${card.id().toString()}">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.from")}
            <input type="date" name="from" value="${card.from().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("tb.to")}
            <input type="date" name="to" value="${card.to().toString()}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("tb.show")}</button>
    </form>

    <%-- Print сарлавҳа: фақат чоп этишда (компания + контакт + давр) --%>
    <div class="mb-4 hidden print:block">
        <h2 class="mb-1 text-xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${companyName}</h2>
        <div>${msg.localize("contactcard.statement")}: <b>${card.displayName()}</b></div>
        <div>${card.from().toString()} - ${card.to().toString()}</div>
        <div class="text-sm text-current/60">${msg.localize("report.allAmountsIn", homeCurrency)}</div>
    </div>

    !{var stmt = card.statement();}
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
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(stmt.opening())}</td>
        </tr>
        @for(StatementService.Row r : stmt.rows())
            <tr>
                <td class="whitespace-nowrap">${r.date().toString()}</td>
                <td>@if(r.sourceModule() == null)${msg.localize("statement.type.MANUAL")}@else${msg.localize("statement.type." + r.sourceModule())}@endif</td>
                <%-- DEC-063 услуби: рақам линк - ҳужжатга (тур маълум),
                     бўлмаса JE'га; print'да оддий матндек чиқади --%>
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
        @if(stmt.rows().isEmpty())
            <tr><td colspan="6" class="text-current/60">${msg.localize("statement.noActivity")}</td></tr>
        @endif
        </tbody>
        <tfoot>
        <tr>
            <td colspan="5">${msg.localize("statement.closing")}</td>
            <td class="whitespace-nowrap text-right tabular-nums">${Fmt.money(stmt.closing())}</td>
        </tr>
        </tfoot>
    `)
`)
