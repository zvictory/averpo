@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.domain.ClassTrackingMode
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import java.util.List
@param List<ClassOption> classes
@param String mode
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.classes", title = msg.lookup("cls.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("cls.title")}</h1>
        <div class="flex items-center gap-3">
            <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
        </div>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:760px;display:grid;gap:1rem;">
        <%-- Tracking режими (QBO Preferences) - каталог билан ёнма-ён --%>
        <form method="post" action="/settings/classes/mode"
              class="flex flex-wrap items-end gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[220px] flex-1">
                <span class="inline-flex items-center gap-1">${msg.localize("cls.mode")} @template.shared.help(key = "field.classTracking", msg = msg)</span>
                <select name="mode" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    @for(ClassTrackingMode value : ClassTrackingMode.values())
                        <option value="${value.name()}" selected="${value.name().equals(mode)}">
                            ${msg.localize(value.titleKey())}
                        </option>
                    @endfor
                </select>
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
        </form>

        <%-- Янги йўналиш --%>
        <form method="post" action="/settings/classes"
              class="flex flex-wrap gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <input name="name" required maxlength="100" placeholder="${msg.lookup("cls.col.name")}"
                   class="min-w-[160px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <select name="parentId" class="min-w-[160px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="">${msg.localize("cls.noParent")}</option>
                @for(ClassOption option : classes)
                    @if(option.active())
                        <option value="${option.id().toString()}">${option.fullName()}</option>
                    @endif
                @endfor
            </select>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("cls.add")}</button>
        </form>

        @if(classes.isEmpty())
            <p class="text-current/60">${msg.localize("cls.empty")}</p>
        @else
                @template.shared.table(cls = "min-w-[620px]", c = @`
                    <thead>
                    <tr>
                        <th>${msg.localize("cls.col.name")}</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(ClassOption option : classes)
                        <tr>
                            <td colspan="2">
                                <form method="post" action="/settings/classes/${option.id().toString()}"
                                      style="display:flex;gap:.6rem;align-items:center;flex-wrap:wrap;">
                                    @template.shared.csrf(csrf = csrf)
                                    <%-- Дарахт: чуқурликка қараб indent («Ота:Бола» тўлиқ ном ота select'ида) --%>
                                    <span style="width:${option.depth() * 16}px;flex-shrink:0;"></span>
                                    <input name="name" value="${option.fullName().contains(":") ? option.fullName().substring(option.fullName().lastIndexOf(":") + 1) : option.fullName()}"
                                           required maxlength="100"
                                           class="min-w-[140px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                    <select name="parentId" class="min-w-[140px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                        <option value="">${msg.localize("cls.noParent")}</option>
                                        @for(ClassOption parent : classes)
                                            @if(!parent.id().equals(option.id()))
                                                <option value="${parent.id().toString()}"
                                                        selected="${option.fullName().contains(":") && option.fullName().substring(0, option.fullName().lastIndexOf(":")).equals(parent.fullName())}">
                                                    ${parent.fullName()}
                                                </option>
                                            @endif
                                        @endfor
                                    </select>
                                    <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 whitespace-nowrap">
                                        <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${option.active()}">
                                        ${msg.localize("accounts.form.active")}
                                    </label>
                                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
                                </form>
                            </td>
                        </tr>
                    @endfor
                    </tbody>
            `)
        @endif
    </div>
`)
