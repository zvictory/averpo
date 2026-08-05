@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.ItemType
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<Item> items
@param String typeFilter
@param String activity = "ACTIVE"
@param String q = ""
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.items", title = msg.lookup("item.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("item.title")}</h1>
        @if(canEdit != null && canEdit)
            <div style="display:flex;gap:.5rem;align-items:center;">
                <%-- DEC-024: drawer'да очилади; JS'сиз href fallback --%>
                @template.shared.createButton(msg = msg, href = "/items/new", hxGet = "/items/new")
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" href="/item-categories">${msg.localize("cat.title")}</a>
            </div>
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт каталог филтр қатори (DEC-068): тур + фаоллик + матн --%>
    @template.shared.listFilter(action = "/items", q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.type")}
            <select name="type" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${typeFilter.isEmpty()}">${msg.localize("filter.all")}</option>
                @for(ItemType type : ItemType.values())
                    <option value="${type.name()}" selected="${type.name().equals(typeFilter)}">
                        ${msg.localize("item.type." + type.name())}
                    </option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.active")}
            <select name="activity" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="ACTIVE" selected="${"ACTIVE".equals(activity)}">${msg.localize("filter.active.ACTIVE")}</option>
                <option value="INACTIVE" selected="${"INACTIVE".equals(activity)}">${msg.localize("filter.active.INACTIVE")}</option>
                <option value="ALL" selected="${"ALL".equals(activity)}">${msg.localize("filter.active.ALL")}</option>
            </select>
        </label>
    `)

    @if(items.isEmpty())
        <p class="text-current/60">${msg.localize("item.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[720px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("item.col.name")}</th>
                <th>${msg.localize("item.col.sku")}</th>
                <th>${msg.localize("item.col.type")}</th>
                <th>${msg.localize("item.col.category")}</th>
                <th>${msg.localize("item.col.unit")}</th>
                <%-- U5: нархлар home валютада - сарлавҳада кўрсатилади --%>
                <th class="text-right">${msg.localize("item.col.salesPrice")} (${homeCurrency})</th>
                <th>${msg.localize("accounts.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(Item item : items)
                <%-- T6: қатор босилса таҳрир формаси (фақат таҳрир ҳуқуқида -
                     viewer учун алоҳида кўриш саҳифаси йўқ) --%>
                <tr data-drawer data-href="${canEdit != null && canEdit ? "/items/" + item.getId() + "/edit" : null}">
                    <td>${item.getName()}</td>
                    <td>${item.getSku()}</td>
                    <td>${msg.localize("item.type." + item.getType().name())}</td>
                    <td>@if(item.getCategory() != null)${item.getCategory().getName()}@endif</td>
                    <td>@if(item.getUnit() != null)${item.getUnit().getName()}@endif</td>
                    <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(item.getSalesPrice())}</td>
                    <td>
                        @if(item.isActive())
                            <span class="text-success">${msg.localize("accounts.active")}</span>
                        @else
                            <span class="text-current/50">${msg.localize("accounts.inactive")}</span>
                        @endif
                    </td>
                    <td class="text-center">
                        <%-- BA-007 P1a: Action менюси --%>
                        @if(canEdit != null && canEdit)
                            @template.shared.rowMenu(msg = msg, items = @`
                                <a href="/items/${item.getId().toString()}/edit"
                                   hx-get="/items/${item.getId().toString()}/edit"
                                   hx-target="#drawerBody" hx-swap="innerHTML">${msg.localize("accounts.edit")}</a>
                            `)
                        @endif
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
`)
