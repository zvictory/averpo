@import com.averpo.erp.audit.domain.AuditEvent
@import com.averpo.erp.audit.domain.AuditEventType
@import com.averpo.erp.i18n.Msg
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<AuditEvent> events
@param Map<UUID, String> times
@param Map<UUID, String> clients
@param int page
@param int totalPages
@param int size
@param boolean hasPrev
@param boolean hasNext
@param String from
@param String to
@param String eventType
@param String username
@param String filterQuery
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false

@template.layout.main(helpKey = "page.auditLog", title = msg.lookup("audit.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-4 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("audit.title")}</h1>

    <%-- Аудит ёзуви ўзгармас (append-only) - экранда ҳеч қандай амал йўқ --%>
    <form method="get" action="/audit-log"
          style="display:flex;gap:.75rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap;">
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("entries.filter.from")}
            <input type="date" name="from" value="${from}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("entries.filter.to")}
            <input type="date" name="to" value="${to}"
                   class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("audit.col.event")}
            <select name="eventType" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${eventType.isEmpty()}">${msg.localize("entries.filter.all")}</option>
                @for(AuditEventType type : AuditEventType.values())
                    <option value="${type.name()}" selected="${type.name().equals(eventType)}">
                        ${msg.localize(type.titleKey())}
                    </option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("audit.col.user")}
            <input type="text" name="username" value="${username}" maxlength="50"
                   class="mt-1 block max-w-48 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("entries.filter.apply")}</button>
    </form>

    @if(events.isEmpty())
        <p class="text-current/60">${msg.localize("audit.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[720px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("audit.col.time")}</th>
                <th>${msg.localize("audit.col.user")}</th>
                <th>${msg.localize("audit.col.event")}</th>
                <th>${msg.localize("audit.col.doc")}</th>
                <th>${msg.localize("audit.col.details")}</th>
            </tr>
            </thead>
            <tbody>
            @for(AuditEvent event : events)
                <tr>
                    <td class="whitespace-nowrap">${times.get(event.getId())}</td>
                    <%-- Фон жараён (scheduler/bootstrap) «system» actor'ини «Тизим» кўрсатади (Arbitr-164) --%>
                    <td>@if(event.getUsername().equals("system"))${msg.localize("audit.system")}@else${event.getUsername()}@endif</td>
                    <td>${msg.localize(event.getEventType().titleKey())}</td>
                    <td>
                        @if(event.getEntryId() != null)
                            <a href="/journal-entries/${event.getEntryId().toString()}">${event.getDocNumber()}</a>
                        @elseif(event.getDocNumber() != null)
                            ${event.getDocNumber()}
                        @endif
                    </td>
                    <td>
                        ${event.getDetails()}
                        <%-- IP ёнида қисқа client (Arbitr-062); тўлиқ UA - title'да --%>
                        @if(clients.get(event.getId()) != null)
                            <span class="whitespace-nowrap text-[.85em] text-current/50" title="${event.getUserAgent()}">(${clients.get(event.getId())})</span>
                        @endif
                    </td>
                </tr>
            @endfor
            </tbody>
        `)

        <div style="display:flex;gap:.75rem;align-items:center;margin-top:1rem;flex-wrap:wrap;">
            @if(hasPrev)
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/audit-log?page=${page - 1}${filterQuery}">← ${msg.localize("audit.prev")}</a>
            @endif
            <span class="text-current/60">${msg.localize("audit.page", String.valueOf(page + 1), String.valueOf(totalPages))}</span>
            @if(hasNext)
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/audit-log?page=${page + 1}${filterQuery}">${msg.localize("audit.next")} →</a>
            @endif
        </div>
    @endif
`)
