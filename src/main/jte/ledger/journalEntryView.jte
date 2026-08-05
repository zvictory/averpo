@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.EntryStatus
@import com.averpo.erp.ledger.domain.JournalEntry
@import com.averpo.erp.ledger.domain.JournalEntryLine
@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@param JournalEntry entry
@param String today
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String postedAtText = ""
@param String reversedByNumber = null
@param String reversedById = null
@param String reversalOfId = null
@param String reversalOfNumber = null
@param String sourceUrl = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = entry.getEntryNumber(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">@template.shared.docIcon(type = "journalEntry") ${entry.getEntryNumber()}</h1>
        <a href="/journal-entries" class="text-primary underline dark:text-primary-dark">${msg.localize("entries.view.back")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Arbitr-163: header metadata - ёрлиқ тепада, responsive 1/2 устун --%>
    <div class="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4 rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("entries.view.date")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">${entry.getEntryDate().toString()}</span>
        </div>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("entries.view.status")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">@template.shared.badge(value = entry.getStatus().name(), msg = msg)</span>
        </div>
        <%-- Arbitr-063: хом enum ўрнига тур номи (statement.type.* тўплами) +
             манба ҳужжатга «очиш» линки (mapping SourceDocLinks, қоида 6) --%>
        <div class="flex flex-col gap-0.5">
            <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("entries.view.source")}</span>
            <span class="text-base text-on-surface dark:text-on-surface-dark">
                @if(entry.getSourceModule() == null)
                    ${msg.localize("statement.type.MANUAL")}
                @else
                    ${msg.localize("statement.type." + entry.getSourceModule())}
                @endif
                @if(sourceUrl != null)
                    - <a href="${sourceUrl}" class="text-primary underline dark:text-primary-dark">${msg.localize("entries.view.openSource")} →</a>
                @endif
            </span>
        </div>
        @if(!postedAtText.isEmpty())
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("entries.view.postedAt")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">${postedAtText}</span>
            </div>
        @endif
        <%-- Arbitr-063: сторно линклари иккала йўналишда --%>
        @if(reversedByNumber != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("entries.view.reversedBy")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark">
                    @if(reversedById != null)
                        <a href="/journal-entries/${reversedById}" class="text-primary underline dark:text-primary-dark">${reversedByNumber}</a>
                    @else
                        ${reversedByNumber}
                    @endif
                </span>
            </div>
        @endif
        @if(reversalOfNumber != null && reversalOfId != null)
            <div class="flex flex-col gap-0.5">
                <span class="text-base font-semibold text-on-surface dark:text-on-surface-dark">${msg.localize("entries.view.reversalOf")}</span>
                <span class="text-base text-on-surface dark:text-on-surface-dark"><a href="/journal-entries/${reversalOfId}" class="text-primary underline dark:text-primary-dark">${reversalOfNumber}</a></span>
            </div>
        @endif
    </div>

    @if(entry.getDescription() != null)
        <p class="mb-4">${entry.getDescription()}</p>
    @endif

    @template.shared.table(cls = "min-w-[860px]", wrap = "mb-5", c = @`
        <thead>
        <tr>
            <th>#</th>
            <th>${msg.localize("entries.col.account")}</th>
            <th class="text-right">${msg.localize("entries.col.debit")}</th>
            <th class="text-right">${msg.localize("entries.col.credit")}</th>
            <th class="text-right">${msg.localize("entries.col.rate")}</th>
            <th class="text-right">${msg.localize("entries.col.debitBase")}</th>
            <th class="text-right">${msg.localize("entries.col.creditBase")}</th>
            <th>${msg.localize("entries.col.memo")}</th>
        </tr>
        </thead>
        <tbody>
        <%-- U1: home жамини кўрсатиш учун base суммалар шу ерда йиғилади -
             entry балансланган, шунинг учун иккала жами тенг чиқади --%>
        !{var totals = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};}
        @for(JournalEntryLine line : entry.getLines())
            !{var lineMoney = line.getDebit() != null ? line.getDebit() : line.getCredit();}
            !{totals[0] = line.getDebit() != null ? totals[0].add(line.getDebit().getBaseAmount()) : totals[0];}
            !{totals[1] = line.getCredit() != null ? totals[1].add(line.getCredit().getBaseAmount()) : totals[1];}
            <tr>
                <td>${line.getLineNo()}</td>
                <td>
                    @if(line.getAccount().getCode() != null)${line.getAccount().getCode()} - @endif${line.getAccount().getName()}
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getDebit() != null)
                        ${Fmt.money(line.getDebit())}
                    @endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getCredit() != null)
                        ${Fmt.money(line.getCredit())}
                    @endif
                </td>
                <%-- Сатр курси фақат чет валютада кўрсатилади - home сатрда 1 шовқин бўларди.
                     Arbitr-065: йўналиши доим кучли валюта базисида (1 USD = 12 600 UZS) --%>
                <td class="text-right whitespace-nowrap">
                    @if(lineMoney != null && !lineMoney.getCurrency().equals(homeCurrency))
                        !{var rateOr = Fmt.orient(lineMoney.getCurrency(), homeCurrency, lineMoney.getExchangeRate());}
                        ${msg.localize("rate.oriented", rateOr.base(), Fmt.rate(rateOr.value()), rateOr.quote())}
                    @endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getDebit() != null)${Fmt.money(line.getDebit().getBaseAmount())}@endif
                </td>
                <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                    @if(line.getCredit() != null)${Fmt.money(line.getCredit().getBaseAmount())}@endif
                </td>
                <td>${line.getMemo()}</td>
            </tr>
        @endfor
        </tbody>
        <tfoot>
        <tr class="bg-surface-alt font-semibold text-on-surface-strong dark:bg-surface-dark-alt dark:text-on-surface-dark-strong">
            <td colspan="5" class="text-right">${msg.localize("entries.view.totalBase", homeCurrency)}:</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totals[0])}</td>
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(totals[1])}</td>
            <td></td>
        </tr>
        </tfoot>
    `)

    @if(canEdit != null && canEdit && entry.getStatus() == EntryStatus.DRAFT)
        <div style="display:flex;gap:.5rem;">
            <form method="post" action="/journal-entries/${entry.getId().toString()}/post">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("entries.view.post")}</button>
            </form>
            <form method="post" action="/journal-entries/${entry.getId().toString()}/delete"
                  onsubmit="return confirm('${msg.lookup("entries.view.deleteConfirm")}');">
                @template.shared.csrf(csrf = csrf)
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-4 py-2 text-center text-sm font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("entries.view.delete")}</button>
            </form>
        </div>
    @elseif(canEdit != null && canEdit && entry.getStatus() == EntryStatus.POSTED)
        @template.shared.reverseForm(action = "/journal-entries/" + entry.getId().toString() + "/reverse",
            confirmKey = "entries.view.reverseConfirm", labelPrefix = "entries.view",
            today = today, csrf = csrf, msg = msg)
    @endif

    <%-- Иловалар (attachments.md): КЎРИШ экранига hx-get фрагмент --%>
    <div hx-get="/attachments/JOURNAL_ENTRY/${entry.getId().toString()}"
         hx-trigger="load" hx-swap="outerHTML"></div>
`)
