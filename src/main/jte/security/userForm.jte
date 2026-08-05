@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.security.domain.UserRole
@import com.averpo.erp.security.web.UserForm
@import com.averpo.erp.contact.service.ContactService.ContactRef
@import java.util.List
@param UserForm form
@param boolean isNew
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null
@param List<ContactRef> employees = null

@template.layout.main(title = msg.lookup(isNew ? "user.form.titleNew" : "user.form.titleEdit"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize(isNew ? "user.form.titleNew" : "user.form.titleEdit")}</h1>
        <a href="/users" class="text-primary underline dark:text-primary-dark">${msg.localize("user.backToList")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <form method="post" action="${isNew ? "/users" : "/users/" + form.getId()}"
          class="grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("user.form.username")}
            @if(isNew)
                <input name="username" required minlength="3" maxlength="50"
                       value="${form.getUsername()}" autocomplete="off"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <span class="mt-1 block text-[.85rem] text-current/60">${msg.localize("user.form.usernameHint")}</span>
            @else
                <%-- BR-USR-003: username ўзгармайди - read-only матн, лекин
                     қиймат формада боради (service ўзгармаганини текширади) --%>
                <input name="username" value="${form.getUsername()}" readonly
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            @endif
        </label>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("user.form.displayName")}
            <input name="displayName" required value="${form.getDisplayName()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>

        <%-- DEC-092: 8 роль + танланган роль тавсифи (user-roles.md
             «Экранлар»). Alpine r ҳолати танловга эргашади - тавсиф
             серверда тўлиқ render бўлиб фақат кўриниши алмашади
             (JS'сиз ҳам форма ишлайверади, тавсиф эса қулайлик). --%>
        <div x-data="{r:'${form.getRole() == null ? "" : form.getRole()}'}">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.role")}
                <select name="role" required x-model="r"
                        class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    @for(UserRole role : UserRole.values())
                        <option value="${role.name()}" selected="${role.name().equals(form.getRole())}">
                            ${msg.localize("user.role." + role.name())}
                        </option>
                    @endfor
                </select>
            </label>
            @for(UserRole role : UserRole.values())
                <span class="mt-1 block text-[.85rem] text-current/60" x-show="r === '${role.name()}'" x-cloak>
                    ${msg.localize("user.role." + role.name() + ".desc")}
                </span>
            @endfor
        </div>

        @if(!isNew)
            <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-2">
                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${form.isActive()}">
                ${msg.localize("user.form.active")}
            </label>

            <%-- Email (рефайнмент банд 4): admin ихтиёрий киритади - профиль
                 email билан айнан бир майдон (self-service /profile'да ҳам). --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.email")}
                <input type="email" name="email" value="${form.getEmail() == null ? "" : form.getEmail()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>

            <%-- Ходим улаш (DEC-101 4-бўлим): super-admin app_user'ни
                 ходимга (Contact type=EMPLOYEE) боғлайди. Combobox -
                 қидирувли (кўп ходимда топиш осон); quick-add ЙЎҚ - фақат
                 мавжуд ходимлардан танлаш. Фақат фаол EMPLOYEE (BR-USR-015:
                 1:1 фаол). Бўш - уланмаган/узиш. Профилда read-only. --%>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.employee")}
                @template.shared.combobox(name = "employeeContactId", msg = msg,
                        value = form.getEmployeeContactId(),
                        placeholder = msg.lookup("user.form.employeeNone"),
                        cls = "mt-1 w-full",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("user.form.employeeNone"))
                            @for(ContactRef emp : employees)
                                @template.shared.comboOption(value = emp.id().toString(),
                                        label = emp.displayName(),
                                        selected = emp.id().toString().equals(form.getEmployeeContactId()))
                            @endfor
                        `)
                <span class="mt-1 block text-[.85rem] text-current/60">${msg.localize("user.form.employeeHint")}</span>
            </label>
        @endif

        @if(isNew)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.password")}
                <input type="password" name="password" required autocomplete="new-password"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.passwordConfirm")}
                <input type="password" name="passwordConfirm" required autocomplete="new-password"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        @endif

        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
    </form>

    @if(!isNew)
        <%-- Парол алмаштириш АЛОҲИДА карта/форма - асосий формадан ажратилган,
             тасодифан бирга submit бўлмайди (spec) --%>
        <form method="post" action="/users/${form.getId()}/password"
              class="mt-5 grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
            @template.shared.csrf(csrf = csrf)
            <b>${msg.localize("user.form.changePassword")}</b>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.password")}
                <input type="password" name="password" required autocomplete="new-password"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("user.form.passwordConfirm")}
                <input type="password" name="passwordConfirm" required autocomplete="new-password"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("user.form.changePassword")}</button>
        </form>
    @endif
`)
