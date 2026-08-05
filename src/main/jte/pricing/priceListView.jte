@import com.averpo.erp.contact.service.ContactService.ContactRef
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.service.ItemService.ItemRef
@import com.averpo.erp.pricing.domain.PriceList
@import com.averpo.erp.pricing.web.PriceListController.CustomerRow
@import com.averpo.erp.pricing.web.PriceListController.PriceRow
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param PriceList list
@param List<PriceRow> prices
@param List<CustomerRow> customers
<%-- PERF-018: select'лар енгил DTO - тўлиқ entity'лар юкланмайди --%>
@param List<ContactRef> freeCustomers
@param List<ItemRef> items
@param List<Currency> currencies
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(title = list.getName(), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${list.getName()}</h1>
        <a href="/settings/price-lists" class="text-primary underline dark:text-primary-dark">${msg.localize("plist.backToList")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:760px;display:grid;gap:1rem;">
        <%-- Сарлавҳа таҳрири --%>
        <form method="post" action="/settings/price-lists/${list.getId().toString()}"
              class="flex flex-wrap items-end gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark min-w-[160px] flex-[2]">
                ${msg.localize("plist.name")}
                <input name="name" value="${list.getName()}" required
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("plist.currency")}
                <%-- Валюта - native select (сурув қарори: 3-10 ёзув) --%>
                <select name="currency" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    @for(Currency currency : currencies)
                        <option value="${currency.getCode()}"
                                selected="${currency.getCode().equals(list.getCurrency().getCode())}">
                            ${currency.getCode()}
                        </option>
                    @endfor
                </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("plist.validFrom")}
                <input type="date" name="validFrom"
                       value="${list.getValidFrom() == null ? "" : list.getValidFrom().toString()}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("plist.validTo")}
                <input type="date" name="validTo"
                       value="${list.getValidTo() == null ? "" : list.getValidTo().toString()}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 whitespace-nowrap pb-1.5">
                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="defaultList" value="true" checked="${list.isDefaultList()}">
                ${msg.localize("plist.default")}
            </label>
            <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 whitespace-nowrap pb-1.5">
                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${list.isActive()}">
                ${msg.localize("accounts.form.active")}
            </label>
            <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
        </form>
        <p class="-mt-2 text-[.85rem] text-current/60">${msg.localize("plist.defaultHint")}</p>

        <%-- Поғонали нархлар --%>
        <div class="grid gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
            <b>${msg.localize("plist.prices")}</b>
            @if(prices.isEmpty())
                <p class="text-[.9rem] text-current/60">${msg.localize("plist.pricesEmpty")}</p>
            @else
                <div style="display:grid;gap:.4rem;">
                    @for(PriceRow row : prices)
                        <div class="flex flex-wrap items-center gap-2.5 border-t border-outline pt-1.5 dark:border-outline-dark">
                            <span style="flex:2;min-width:140px;">${row.itemName()}</span>
                            <%-- «100+ дона → 9 000» ўқилиши --%>
                            <span class="text-current/60">${Fmt.qty(row.minQuantity(), row.unitName())}+ →</span>
                            <b>${Fmt.money(row.price())} ${list.getCurrency().getCode()}</b>
                            <form method="post"
                                  action="/settings/price-lists/${list.getId().toString()}/prices/${row.id().toString()}/delete"
                                  style="margin-left:auto;">
                                @template.shared.csrf(csrf = csrf)
                                <button type="submit"
                                        class="cursor-pointer border-0 bg-transparent p-0 text-[.85rem] text-current/50 underline">
                                    ${msg.localize("plist.remove")}
                                </button>
                            </form>
                        </div>
                    @endfor
                </div>
            @endif
            <%-- Поғона қўшиш: item + миқдордан бошлаб + нарх --%>
            <form method="post" action="/settings/price-lists/${list.getId().toString()}/prices"
                  class="flex flex-wrap items-center gap-2 border-t border-outline pt-2.5 dark:border-outline-dark">
                @template.shared.csrf(csrf = csrf)
                @template.shared.combobox(name = "itemId", msg = msg,
                        placeholder = msg.lookup("plist.selectItem"),
                        required = true,
                        cls = "min-w-[150px] flex-[2]",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("plist.selectItem"))
                            @for(ItemRef item : items)
                                @template.shared.comboOption(value = item.id().toString(), label = item.name())
                            @endfor
                        `)
                <input name="minQuantity" inputmode="decimal" placeholder="${msg.lookup("plist.minQty")}"
                       class="min-w-[90px] flex-1 text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <span>+ →</span>
                <input name="price" inputmode="decimal" required placeholder="${msg.lookup("plist.price")}"
                       class="min-w-[100px] flex-1 text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <span class="text-current/50">${list.getCurrency().getCode()}</span>
                <%-- X-010: ўз модул калити (unit.add эмас) --%>
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("plist.add")}</button>
            </form>
            <p class="text-sm text-current/60">${msg.localize("plist.priceHint")}</p>
        </div>

        <%-- Мижоз бириктируви --%>
        <div class="grid gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
            <b>${msg.localize("plist.customers")}</b>
            @if(customers.isEmpty())
                <p class="text-[.9rem] text-current/60">${msg.localize("plist.customersEmpty")}</p>
            @else
                <div style="display:grid;gap:.4rem;">
                    @for(CustomerRow row : customers)
                        <div class="flex items-center gap-2.5 border-t border-outline pt-1.5 dark:border-outline-dark">
                            <span>${row.name()}</span>
                            <form method="post"
                                  action="/settings/price-lists/${list.getId().toString()}/customers/${row.customerId().toString()}/remove"
                                  style="margin-left:auto;">
                                @template.shared.csrf(csrf = csrf)
                                <button type="submit"
                                        class="cursor-pointer border-0 bg-transparent p-0 text-[.85rem] text-current/50 underline">
                                    ${msg.localize("plist.remove")}
                                </button>
                            </form>
                        </div>
                    @endfor
                </div>
            @endif
            @if(!freeCustomers.isEmpty())
                <form method="post" action="/settings/price-lists/${list.getId().toString()}/customers"
                      class="flex flex-wrap items-center gap-2 border-t border-outline pt-2.5 dark:border-outline-dark">
                    @template.shared.csrf(csrf = csrf)
                    @template.shared.combobox(name = "customerId", msg = msg,
                            placeholder = msg.lookup("plist.selectCustomer"),
                            required = true,
                            cls = "min-w-[160px] flex-[2]",
                            options = @`
                                @template.shared.comboOption(value = "", label = msg.lookup("plist.selectCustomer"))
                                @for(ContactRef customer : freeCustomers)
                                    @template.shared.comboOption(value = customer.id().toString(), label = customer.displayName())
                                @endfor
                            `)
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("plist.assign")}</button>
                </form>
                <p class="text-sm text-current/60">${msg.localize("plist.assignHint")}</p>
            @endif
        </div>
    </div>
`)
