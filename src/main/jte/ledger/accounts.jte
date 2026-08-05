@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.AccountClassification
@import com.averpo.erp.ledger.web.AccountController
@import java.util.List
@param List<AccountController.TreeRow> rows
@param String homeCurrency
@param String q = ""
@param String activity = "ALL"
@param String classification = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.accounts", title = msg.lookup("accounts.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("accounts.title")}</h1>
        @if(canEdit != null && canEdit)
            <div style="display:flex;gap:.5rem;align-items:center;">
                <%-- DEC-024: drawer'да очилади; JS'сиз href fallback --%>
                @template.shared.createButton(msg = msg, href = "/accounts/new", hxGet = "/accounts/new")
                <%-- QBO услуби: кам ишлатиладиган амаллар уч нуқта менюда.
                     Penguin dropdown (DEC-120, реф: docs/penguin-refs/
                     dropdown-menu.html); панел absolute - жадвал
                     ташқарисида overflow муаммоси йўқ. Бандлар услуби
                     панел тегидаги [&_button] variant'ларида; файл
                     танлаш банди label - унга класс тўғридан-тўғри. --%>
                <div class="relative" x-data="{open:false}">
                    <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark"
                            aria-label="${msg.lookup("common.actions")}" aria-haspopup="menu" x-bind:aria-expanded="open ? 'true' : 'false'" x-on:click="open = !open">⋮</button>
                    <div x-show="open" x-on:click.outside="open = false" x-on:keydown.escape.window="open = false" x-cloak role="menu"
                         class="absolute right-0 top-full z-30 mt-1 flex w-max min-w-60 flex-col overflow-hidden rounded-radius border border-outline bg-surface-alt shadow-lg dark:border-outline-dark dark:bg-surface-dark-alt [&_button]:block [&_button]:w-full [&_button]:cursor-pointer [&_button]:border-0 [&_button]:bg-transparent [&_button]:px-4 [&_button]:py-2 [&_button]:text-left [&_button]:text-sm [&_button]:text-on-surface [&_button:hover]:bg-surface-dark-alt/5 [&_button:hover]:text-on-surface-strong dark:[&_button]:text-on-surface-dark dark:[&_button:hover]:bg-surface-alt/5 dark:[&_button:hover]:text-on-surface-dark-strong">
                        <form method="post" action="/accounts/import-default">
                            @template.shared.csrf(csrf = csrf)
                            <button type="submit">${msg.localize("accounts.importDefault")}</button>
                        </form>
                        <form method="post" action="/accounts/import" enctype="multipart/form-data">
                            @template.shared.csrf(csrf = csrf)
                            <label class="block cursor-pointer px-4 py-2 text-sm text-on-surface hover:bg-surface-dark-alt/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-surface-alt/5 dark:hover:text-on-surface-dark-strong">
                                ${msg.localize("accounts.importCsv")}
                                <input type="file" name="file" accept=".csv" class="hidden" onchange="this.form.submit()">
                            </label>
                        </form>
                        <div class="border-t border-outline px-4 py-2 text-xs text-current/60 dark:border-outline-dark">
                            ${msg.localize("accounts.csvFormat")}
                        </div>
                    </div>
                </div>
            </div>
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт каталог филтр қатори (DEC-068): матн + фаоллик +
         classification. Default (ҳаммаси) - тўлиқ дарахт; филтр танланса
         текис рўйхат (иерархия узилмасин деб контроллер шундай беради) --%>
    @template.shared.listFilter(action = "/accounts", q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.type")}
            <select name="classification" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${classification.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(AccountClassification cls : AccountClassification.values())
                    <option value="${cls.name()}" selected="${cls.name().equals(classification)}">
                        ${msg.localize(cls.titleKey())}
                    </option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.active")}
            <select name="activity" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="ALL" selected="${"ALL".equals(activity)}">${msg.localize("filter.active.ALL")}</option>
                <option value="ACTIVE" selected="${"ACTIVE".equals(activity)}">${msg.localize("filter.active.ACTIVE")}</option>
                <option value="INACTIVE" selected="${"INACTIVE".equals(activity)}">${msg.localize("filter.active.INACTIVE")}</option>
            </select>
        </label>
    `)

    @if(rows.isEmpty())
        <p class="text-current/60">${msg.localize("accounts.empty")}</p>
    @else
        <div x-data="{c:{}}">
            <div class="mb-2 text-[.85rem]">
                <a href="#" x-on:click.prevent="c = {}"
                   class="mr-3 text-primary underline dark:text-primary-dark">${msg.localize("accounts.expandAll")}</a>
                <a href="#" x-on:click.prevent="(window.averpoGroupIds || []).forEach(id => c[id] = true)"
                   class="text-primary underline dark:text-primary-dark">${msg.localize("accounts.collapseAll")}</a>
            </div>
            @template.shared.table(cls = "min-w-[820px]", c = @`
                <thead>
                <tr>
                    <%-- DEC-023: Number алоҳида чап устун (QBO CoA) --%>
                    <th class="w-[5.5rem]">${msg.localize("accounts.col.code")}</th>
                    <th class="min-w-[240px]">${msg.localize("accounts.col.name")}</th>
                    <th>${msg.localize("accounts.col.detailType")}</th>
                    <th>${msg.localize("accounts.col.type")}</th>
                    <th>${msg.localize("accounts.col.currency")}</th>
                    <th class="text-right">${msg.localize("accounts.col.balance")}</th>
                    <th>${msg.localize("accounts.col.status")}</th>
                    <th>${msg.localize("common.actions")}</th>
                </tr>
                </thead>
                <tbody>
                @for(AccountController.TreeRow row : rows)
                    <%-- T1: postable счёт қатори босилса «Счёт амаллари» (QBO
                         Account history); группа счётда GL сатрлари йўқ - босилмайди.
                         Edit ҳаволаси қатор ичида ишлашда қолади (T0 истисноси) --%>
                    <tr x-show="!(${row.hiddenExpr()})"
                        data-href="${row.account().isPostable() ? "/accounts/" + row.account().getId() + "/transactions" : null}">
                        <%-- DEC-023: кодлар текис чап колонкада - кўз билан тез топилади --%>
                        <td class="whitespace-nowrap tabular-nums">
                            @if(row.account().getCode() != null)${row.account().getCode()}@endif
                        </td>
                        <td style="padding-left:${0.75 + row.depth() * 1.5}rem;">
                            <span style="display:inline-flex;align-items:center;gap:.35rem;">
                                @if(row.hasChildren())
                                    <button type="button"
                                            x-on:click="c['${row.account().getId().toString()}'] = !c['${row.account().getId().toString()}']"
                                            x-text="c['${row.account().getId().toString()}'] ? '▸' : '▾'"
                                            class="w-[1.2rem] cursor-pointer border-0 bg-transparent text-on-surface/70 dark:text-on-surface-dark/70">▾</button>
                                @else
                                    <span style="width:1.2rem;display:inline-block;"></span>
                                @endif
                                @if(!row.account().isPostable())
                                    <b>${row.account().getName()}</b>
                                @else
                                    ${row.account().getName()}
                                @endif
                            </span>
                        </td>
                        <td>${msg.localize(row.account().getDetailType().titleKey())}</td>
                        <%-- DEC-149: тур устуни - AccountType номи (i18n) ва юқори
                             синфи (classification) badge'и ёнма-ён (фойдаланувчи
                             2026-07-18: иккови билла). Badge инглизча (label =
                             englishName - locale'дан қатъи назар english) ва юмшоқ
                             ранг (soft харита badge.jte'да). Майда тур (detailType)
                             чап устунда қолади --%>
                        <td>
                            !{var type = row.account().getType();}
                            <div class="flex flex-wrap items-center gap-1.5">
                                <span>${msg.localize(type.titleKey())}</span>
                                @template.shared.badge(value = type.getClassification().name(), family = "classification", label = type.getClassification().englishName(), msg = msg)
                            </div>
                        </td>
                        <%-- DEC-161: валютага боғланган турда (Bank/AR/AP/Credit Card)
                             валюта коди кўринади - home бўлса ҳам (QBO нақши; currency
                             null = home коди). Бошқа турда бўш (валютаси йўқ) --%>
                        <td>@if(row.account().getType().isCurrencyDenominated())${row.account().getCurrency() != null ? row.account().getCurrency().getCode() : homeCurrency}@endif</td>
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                            <%-- DEC-023: пул доим валютаси билан; қиймат home'да
                                 (GL base) - шунинг учун код ҳам home'ники. Манфий
                                 қолдиқ қизил кўринади --%>
                            @if(row.balance() != null)
                                <span class="${row.balance().signum() < 0 ? "text-danger" : ""}">${com.averpo.erp.shared.web.Fmt.money(row.balance())} ${homeCurrency}</span>
                            @endif
                        </td>
                        <td>
                            @if(row.account().isActive())
                                <span class="text-success">${msg.localize("accounts.active")}</span>
                            @else
                                <span class="text-current/50">${msg.localize("accounts.inactive")}</span>
                            @endif
                        </td>
                        <td class="text-right whitespace-nowrap">
                            <%-- DEC-023: «Счёт амаллари» - кўринган яшил амал
                                 (QBO Account history); Таҳрир ⋮ да қолади ва
                                 drawer'да очилади (DEC-024) --%>
                            @if(row.account().isPostable())
                                <a href="/accounts/${row.account().getId().toString()}/transactions"
                                   class="font-semibold text-success">${msg.localize("acctTx.title")}</a>
                            @endif
                            @if(canEdit != null && canEdit)
                                @template.shared.rowMenu(msg = msg, items = @`
                                    <a href="/accounts/${row.account().getId().toString()}/edit"
                                       hx-get="/accounts/${row.account().getId().toString()}/edit"
                                       hx-target="#drawerBody" hx-swap="innerHTML">${msg.localize("accounts.edit")}</a>
                                `)
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
            `)
            <script>
                window.averpoGroupIds = [
                    @for(AccountController.TreeRow row : rows)
                        @if(row.hasChildren())"${row.account().getId().toString()}",@endif
                    @endfor
                ];
            </script>
        </div>
    @endif
`)
