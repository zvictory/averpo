@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.ItemCategory
@import com.averpo.erp.item.service.ItemCategoryService
@import java.util.List
@param List<ItemCategoryService.CategoryNode> nodes
@param List<ItemCategory> all
@param ItemCategoryService.CategoryEdit editing = null
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String editId = null
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.categories", title = msg.lookup("cat.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("cat.title")}</h1>
        <div class="flex items-center gap-3">
            <a href="/items" class="text-primary underline dark:text-primary-dark">${msg.localize("cat.backToItems")}</a>
        </div>
    </div>

    @template.shared.alert(message = message, error = error)

    @if(canEdit != null && canEdit)
        <form method="post" action="${editId == null ? "/item-categories" : "/item-categories/" + editId}"
              class="mb-5 flex max-w-[720px] flex-wrap items-end gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[180px] flex-1">
                ${msg.localize("cat.name")}
                <input name="name" required value="${editing == null ? "" : editing.name()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[180px] flex-1">
                ${msg.localize("cat.parent")}
                <select name="parentId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="">${msg.localize("accounts.form.parentNone")}</option>
                    @for(ItemCategory category : all)
                        @if(editing == null || !category.getId().equals(editing.id()))
                            <option value="${category.getId().toString()}"
                                    selected="${editing != null && category.getId().equals(editing.parentId())}">
                                ${category.getName()}
                            </option>
                        @endif
                    @endfor
                </select>
            </label>
            @if(editing != null)
                <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 pb-2">
                    <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${editing.active()}">
                    ${msg.localize("accounts.form.active")}
                </label>
            @endif
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("common.save")}</button>
            @if(editing != null)
                <a href="/item-categories" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark">${msg.localize("common.cancel")}</a>
            @endif
        </form>
    @endif

    @if(nodes.isEmpty())
        <p class="text-current/60">${msg.localize("cat.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[480px]", wrap = "max-w-[720px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("cat.name")}</th>
                <th>${msg.localize("accounts.col.status")}</th>
                <th class="text-center">${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(ItemCategoryService.CategoryNode node : nodes)
                <tr>
                    <td style="padding-left:${0.75 + node.depth() * 1.5}rem;">${node.category().getName()}</td>
                    <td>
                        @if(node.category().isActive())
                            <span class="text-success">${msg.localize("accounts.active")}</span>
                        @else
                            <span class="text-current/50">${msg.localize("accounts.inactive")}</span>
                        @endif
                    </td>
                    <td class="text-center">
                        <%-- QA-008: rowMenu қолипи - item/list.jte билан бир хил --%>
                        @if(canEdit != null && canEdit)
                            @template.shared.rowMenu(msg = msg, items = @`
                                <a href="/item-categories?edit=${node.category().getId().toString()}">${msg.localize("accounts.edit")}</a>
                            `)
                        @endif
                    </td>
                </tr>
            @endfor
            </tbody>
        `)
    @endif
`)
