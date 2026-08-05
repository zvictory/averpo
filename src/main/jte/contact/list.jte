@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param String kind
@param Boolean isCustomer
@param Boolean isEmployee = false
@param List<Contact> contacts
@param String activity = "ACTIVE"
@param String q = ""
@param String homeCurrency = ""
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.contacts", title = isEmployee != null && isEmployee ? msg.lookup("contact.employees.title") : (isCustomer ? msg.lookup("contact.customers.title") : msg.lookup("contact.vendors.title")), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${isEmployee != null && isEmployee ? msg.localize("contact.employees.title") : (isCustomer ? msg.localize("contact.customers.title") : msg.localize("contact.vendors.title"))}</h1>
        @if(canEdit != null && canEdit)
            <%-- Arbitr-024: drawer'да очилади; JS'сиз href fallback.
                 Arbitr-133: лабел энди kind'га боғлиқ эмас - ягона
                 «Яратиш» (contact.new.* учталаси ўлик қолди) --%>
            @template.shared.createButton(msg = msg, href = "/" + kind + "/new", hxGet = "/" + kind + "/new")
        @endif
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Стандарт каталог филтр қатори (Arbitr-068): фаоллик + матн
         (эски showInactive тогли ўрнида; тур - route'нинг ўзи) --%>
    @template.shared.listFilter(action = "/" + kind, q = q, msg = msg, extra = @`
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("filter.active")}
            <select name="activity" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="ACTIVE" selected="${"ACTIVE".equals(activity)}">${msg.localize("filter.active.ACTIVE")}</option>
                <option value="INACTIVE" selected="${"INACTIVE".equals(activity)}">${msg.localize("filter.active.INACTIVE")}</option>
                <option value="ALL" selected="${"ALL".equals(activity)}">${msg.localize("filter.active.ALL")}</option>
            </select>
        </label>
    `)

    @if(contacts.isEmpty())
        <p class="text-current/60">${msg.localize("contact.empty")}</p>
    @else
        @template.shared.table(cls = "min-w-[680px]", c = @`
            <thead>
            <tr>
                <th>${msg.localize("contact.col.name")}</th>
                <%-- Nargiza-043: ходимга «Компания»/«Валюта» маъносиз -
                     ўрнига «Ойлик» (oklad home валютада) --%>
                @if(isEmployee != null && isEmployee)
                    <th class="text-right">${msg.localize("contact.col.salary")}</th>
                @else
                    <th>${msg.localize("contact.col.company")}</th>
                @endif
                <th>${msg.localize("contact.col.email")}</th>
                <th>${msg.localize("contact.col.phone")}</th>
                @if(isEmployee == null || !isEmployee)
                    <th>${msg.localize("contact.col.currency")}</th>
                @endif
                <th>${msg.localize("accounts.col.status")}</th>
                <th>${msg.localize("common.actions")}</th>
            </tr>
            </thead>
            <tbody>
            @for(Contact contact : contacts)
                <%-- T7 ниҳоят тўғри ҳолда (Arbitr-002): мижоз/таъминотчи
                     қатори КОНТАКТ КАРТОЧКАСига (кўриш) - барча роль, VIEWER
                     ҳам; ходим қатори эса эски таҳрир drawer'ида (ходим
                     картаси 2-босқич). --%>
                <%-- Ходим: таҳрир (drawer, canEdit'да); мижоз/таъминотчи:
                     карточка (кўриш, барча роль). Битта <tr> - JTE HTML
                     тег баланси шартли очиш тегини рад этади. --%>
                !{boolean emp = isEmployee != null && isEmployee;}
                !{String rowHref = emp
                        ? (canEdit != null && canEdit ? "/" + kind + "/" + contact.getId() + "/edit" : null)
                        : "/" + kind + "/" + contact.getId();}
                <tr data-drawer="${emp && rowHref != null}" data-href="${rowHref}">
                    <td>${contact.getDisplayName()}</td>
                    @if(emp)
                        <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">
                            @if(contact.getMonthlySalary() != null)
                                ${Fmt.money(contact.getMonthlySalary())} ${homeCurrency}
                            @endif
                        </td>
                    @else
                        <td>${contact.getCompanyName()}</td>
                    @endif
                    <td>${contact.getEmail()}</td>
                    <td>${contact.getPhone()}</td>
                    @if(!emp)
                        <td>@if(contact.getCurrency() != null)${contact.getCurrency().getCode()}@endif</td>
                    @endif
                    <td>
                        @if(contact.isActive())
                            <span class="text-success">${msg.localize("accounts.active")}</span>
                        @else
                            <span class="text-current/50">${msg.localize("accounts.inactive")}</span>
                        @endif
                    </td>
                    <td class="text-center">
                        <%-- Nargiza-007 P1a: Action менюси --%>
                        @if(canEdit != null && canEdit)
                            @template.shared.rowMenu(msg = msg, items = @`
                                <a href="/${kind}/${contact.getId().toString()}/edit"
                                   hx-get="/${kind}/${contact.getId().toString()}/edit"
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
