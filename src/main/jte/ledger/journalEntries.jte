@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.EntryStatus
@import com.averpo.erp.ledger.domain.JournalEntry
@import com.averpo.erp.web.Perms
@import java.time.LocalDate
@import java.util.List
@param List<JournalEntry> entries
@param LocalDate from
@param LocalDate to
@param org.springframework.data.domain.Page<JournalEntry> page = null
@param String filterQuery = ""
@param String pageQuery = ""
@param String sortKey = null
@param String sortDir = null
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String status = ""
@param String q = ""
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

<%-- Arbitr-106: сайдбар/панель филтри каби жорий фойдаланувчи имконияти
     SecurityContext'дан (Perms) - «Даврни ёпиш» тугмаси PERIOD_CLOSE
     эгаларигагина кўринади. Server ҳақиқати SecurityConfig'да
     (@PreAuthorize saveClosingDate'да) - бу фақат кўриниш. --%>
!{var perms = Perms.current();}
@template.layout.main(helpKey = "page.journalEntries", title = msg.lookup("entries.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("entries.title")}</h1>
        <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
            <%-- Arbitr-106: «Даврни ёпиш» кириш нуқтаси - сайдбардан кўчди.
                 Фақат PERIOD_CLOSE эгаларига (SUPER_ADMIN, CHIEF_ACCOUNTANT):
                 092 дизайн шарти сақланади - CHIEF SETTINGS'сиз ҳам даврни
                 айнан шу тугма орқали бошқаради. Иккиламчи амал → outline
                 кўриниш (оқ фон + primary контур, Penguin каноник шакли
                 surface/primary токенлари билан), «Янги» асосий ўнгда. --%>
            @if(perms.periodClose())
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-surface px-4 py-2 text-center text-sm font-medium tracking-wide text-primary transition hover:bg-primary/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:outline-offset-0 dark:border-primary-dark dark:bg-surface-dark dark:text-primary-dark dark:focus-visible:outline-primary-dark" href="/settings/closing-date">${msg.localize("nav.closingDate")}</a>
            @endif
            @if(canEdit != null && canEdit)
                @template.shared.createButton(msg = msg, href = "/journal-entries/new")
            @endif
        </div>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт филтр қатори (Arbitr-068): давр + статус + матн
         (давр default'и - жорий йил, аввалги хатти-ҳаракат) --%>
    @template.shared.listFilter(action = "/journal-entries", from = from.toString(), to = to.toString(), q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.status")}
            <select name="status" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${status.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(EntryStatus st : EntryStatus.values())
                    <option value="${st.name()}" selected="${st.name().equals(status)}">
                        ${msg.localize("status." + st.name())}
                    </option>
                @endfor
            </select>
        </label>
    `)

    @if(entries.isEmpty())
        <p class="text-current/60">${msg.localize("entries.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[640px]", c = @`
            <%-- Устун саралаш (ARBITR-105б): whitelist'даги устунлар
                 sortth линк (description - узун эркин матн, actions -
                 маъносиз, оддий th қолади). --%>
            <thead>
            <tr>
                @template.shared.sortth(key = "number", label = msg.lookup("entries.col.number"), baseUrl = "/journal-entries", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "date", label = msg.lookup("entries.col.date"), baseUrl = "/journal-entries", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                <th>${msg.localize("entries.col.description")}</th>
                @template.shared.sortth(key = "source", label = msg.lookup("entries.col.source"), baseUrl = "/journal-entries", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                @template.shared.sortth(key = "status", label = msg.lookup("entries.col.status"), baseUrl = "/journal-entries", sortKey = sortKey, sortDir = sortDir, filterQuery = filterQuery)
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(JournalEntry entry : entries)
                <%-- T3: қатор босилса кўриш саҳифаси (DRAFT'да ҳам - алоҳида
                     таҳрир формаси ҳали йўқ, view'да post/delete амаллари бор) --%>
                <tr data-href="/journal-entries/${entry.getId().toString()}">
                    <td><a href="/journal-entries/${entry.getId().toString()}">${entry.getEntryNumber()}</a></td>
                    <td>${entry.getEntryDate().toString()}</td>
                    <td>${entry.getDescription()}</td>
                    <td>${entry.getSourceModule()}</td>
                    <td>
                        @template.shared.badge(value = entry.getStatus().name(), msg = msg)
                    </td>
                    <td class="text-center">
                        <%-- Nargiza-007 P1a: Action менюси --%>
                        @template.shared.rowMenu(msg = msg, items = @`
                            <a href="/journal-entries/${entry.getId().toString()}">${msg.localize("common.view")}</a>
                        `)
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
    <%-- Саҳифалаш (Beruniy-perf1) - филтрлар линкларда сақланади.
         pageQuery = филтр + жорий sort (105б) - варақлаганда тартиб сақланади --%>
    @template.shared.pagination(page = page, baseUrl = "/journal-entries", filterQuery = pageQuery, msg = msg)
`)
