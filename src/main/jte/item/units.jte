@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.Unit
@import java.util.List
@param List<Unit> units
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.units", title = msg.lookup("unit.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("unit.title")}</h1>
        <div class="flex items-center gap-3">
            <%-- Стандарт гуруҳларни юклаш (DEC-147б): chart importDefault
                 нақши - POST форма + CSRF, secondary тугма. Idempotent, шунга
                 доим кўринади (бор гуруҳга тегмайди). --%>
            <form method="post" action="/settings/units/import-default">
                @template.shared.csrf(csrf = csrf)
                <button type="submit" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark">${msg.localize("unit.importDefault")}</button>
            </form>
            <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
        </div>
    </div>

    <%-- Таблар: бирликлар (жорий) | гуруҳлар - конверсия алоҳида саҳифада --%>
    <div class="mb-4 flex gap-1 border-b-2 border-outline dark:border-outline-dark">
        <a class="-mb-0.5 border-b-2 border-primary px-4 py-2 font-semibold text-primary no-underline dark:border-primary-dark dark:text-primary-dark" href="/settings/units">${msg.localize("unit.tab.units")}</a>
        <a class="px-4 py-2 text-current/60 no-underline" href="/settings/units/groups">${msg.localize("unit.tab.groups")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:720px;display:grid;gap:1rem;">
        <form method="post" action="/settings/units"
              class="flex gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <input name="name" required placeholder="${msg.lookup("unit.name")}"
                   class="flex-1 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("unit.add")}</button>
        </form>

        @if(units.isEmpty())
            <p class="text-current/60">${msg.localize("unit.empty")}</p>
        @else
                @template.shared.table(cls = "min-w-[560px]", c = @`
                    <thead>
                    <tr>
                        <th>${msg.localize("unit.name")}</th>
                        <th>${msg.localize("unit.group")}</th>
                        <th class="text-right">${msg.localize("unit.factor")}</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(Unit unit : units)
                        <tr>
                            <td colspan="4">
                                <form method="post" action="/settings/units/${unit.getId().toString()}"
                                      style="display:flex;gap:.6rem;align-items:center;flex-wrap:wrap;">
                                    @template.shared.csrf(csrf = csrf)
                                    <input name="name" value="${unit.getName()}" required
                                           class="min-w-[130px] flex-[2] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                                    <%-- Гуруҳ/factor фақат кўрсатилади - бошқаруви «Гуруҳлар» табида --%>
                                    <span class="min-w-[110px] flex-[2] text-[.9rem] text-current/60">
                                        @if(unit.getGroup() != null)
                                            ${unit.getGroup().getName()}@if(unit.isBase()) · base@endif
                                        @else
                                            ${msg.localize("unit.group.none")}
                                        @endif
                                    </span>
                                    <span class="min-w-[70px] flex-1 text-right text-[.9rem] text-current/60">
                                        ${unit.getFactor().stripTrailingZeros().toPlainString()}
                                    </span>
                                    <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 whitespace-nowrap">
                                        <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${unit.isActive()}">
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
