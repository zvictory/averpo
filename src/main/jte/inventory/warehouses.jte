@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import java.util.List
@param List<Warehouse> warehouses
@param String activity = "ALL"
@param String q = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.warehouses", title = msg.lookup("warehouse.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("warehouse.title")}</h1>
        <div class="flex items-center gap-3">
            <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
        </div>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:760px;display:grid;gap:1rem;">
        <%-- auto-fit minmax: 375px'да устунлар ўз-ўзидан пастга ўралади,
             қатъий 180px+120px track'лар тор экранни ёриб юборарди --%>
        <form method="post" action="/settings/warehouses"
              class="grid items-end gap-2 [grid-template-columns:repeat(auto-fit,minmax(150px,1fr))] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("warehouse.name")}
                <input name="name" required class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("warehouse.code")}
                <input name="code" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("warehouse.add")}</button>
        </form>

        <%-- Стандарт каталог филтр қатори (DEC-068): фаоллик + матн --%>
        @template.shared.listFilter(action = "/settings/warehouses", q = q, msg = msg, extra = @`
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.active")}
                <select name="activity" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="ALL" selected="${"ALL".equals(activity)}">${msg.localize("filter.active.ALL")}</option>
                    <option value="ACTIVE" selected="${"ACTIVE".equals(activity)}">${msg.localize("filter.active.ACTIVE")}</option>
                    <option value="INACTIVE" selected="${"INACTIVE".equals(activity)}">${msg.localize("filter.active.INACTIVE")}</option>
                </select>
            </label>
        `)

        @if(warehouses.isEmpty())
            <p class="text-current/60">${msg.localize("warehouse.empty")}</p>
        @else
            @template.shared.table(c = @`
                <thead>
                <tr>
                    <th>${msg.localize("warehouse.name")}</th>
                    <th>${msg.localize("warehouse.code")}</th>
                    <th>${msg.localize("accounts.form.active")}</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                @for(Warehouse warehouse : warehouses)
                    <tr>
                        <td colspan="4">
                            <form method="post" action="/settings/warehouses/${warehouse.getId().toString()}"
                                  style="display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:.5rem;align-items:center;">
                                @template.shared.csrf(csrf = csrf)
                                <%-- width:100% шарт: усиз input default ~20 белги кенглигида
                                     қолиб, мобилдаги тор grid track'дан тошиб чиқади --%>
                                <input name="name" value="${warehouse.getName()}" required class="w-full rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                <input name="code" value="${warehouse.getCode()}" class="w-full rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 whitespace-nowrap">
                                    <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${warehouse.isActive()}">
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
