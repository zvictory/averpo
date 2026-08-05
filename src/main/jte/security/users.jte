@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.security.domain.AppUser
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param List<AppUser> users
@param Map<UUID, String> lockedTexts
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.users", title = msg.lookup("user.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("user.title")}</h1>
        @template.shared.createButton(msg = msg, href = "/users/new")
    </div>

    @template.shared.alert(message = message, error = error)

    @template.shared.table(cls = "min-w-[640px]", c = @`
        <thead>
        <tr>
            <th>${msg.localize("user.col.username")}</th>
            <th>${msg.localize("user.col.name")}</th>
            <th>${msg.localize("user.col.role")}</th>
            <th>${msg.localize("user.col.status")}</th>
            <th>${msg.localize("user.col.locked")}</th>
            <th>${msg.localize("common.actions")}</th>
        </tr>
        </thead>
        <tbody>
        @for(AppUser user : users)
            <%-- Каталог қоидаси: қатор босилса таҳрир формаси (T0 механизми) --%>
            <tr data-href="/users/${user.getId().toString()}/edit">
                <td>${user.getUsername()}</td>
                <td>${user.getDisplayName()}</td>
                <td>${msg.localize("user.role." + user.getRole().name())}</td>
                <td>
                    @if(user.isActive())
                        <span class="font-semibold text-success">${msg.localize("accounts.active")}</span>
                    @else
                        <span class="font-semibold text-current/50">${msg.localize("accounts.inactive")}</span>
                    @endif
                </td>
                <td>
                    @if(lockedTexts.containsKey(user.getId()))
                        <span class="text-current/50">${msg.localize("user.lockedUntil", lockedTexts.get(user.getId()))}</span>
                    @endif
                </td>
                <td class="text-center">
                    <%-- Nargiza-007 P1a: Action менюси --%>
                    @template.shared.rowMenu(msg = msg, items = @`
                        <a href="/users/${user.getId().toString()}/edit">${msg.localize("accounts.edit")}</a>
                    `)
                </td>
            </tr>
        @endfor
        </tbody>
    `)
`)
