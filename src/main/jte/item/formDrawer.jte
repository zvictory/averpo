@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.ItemCategory
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.item.service.ItemService
@import com.averpo.erp.item.web.ItemForm
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@import java.util.Map
@param ItemForm form
@param List<ItemCategory> categories
@param List<Unit> units
@param List<Account> incomeAccounts
@param List<Account> expenseAccounts
@param List<Account> assetAccounts
@param List<TaxRate> taxRates
@param Map<String, ItemService.DefaultAccounts> defaultsByType
@param String homeCurrency
@param Msg msg
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String editId = null
@param String error = null
@param Boolean canEdit = false

<%-- DEC-024: drawer жавоби (HX-Request) - #drawerBody ичига тушади. --%>
<div class="flex items-center justify-between gap-3 bg-surface-dark px-5 py-3.5 font-semibold text-on-surface-dark-strong">
    <b>${editId == null ? msg.localize("item.form.titleNew") : msg.localize("item.form.titleEdit")}</b>
    <button type="button" class="cursor-pointer rounded-radius border-0 bg-transparent px-2 py-1 text-xl leading-none text-on-surface-dark transition hover:bg-surface-dark-alt hover:text-on-surface-dark-strong" onclick="drawerClose()" aria-label="${msg.lookup("common.cancel")}">✕</button>
</div>
<div class="drawer-body flex-1 overflow-y-auto p-5">
    @template.item.formFields(form = form, categories = categories, units = units,
            incomeAccounts = incomeAccounts, expenseAccounts = expenseAccounts,
            assetAccounts = assetAccounts, taxRates = taxRates,
            defaultsByType = defaultsByType,
            homeCurrency = homeCurrency, msg = msg, csrf = csrf,
            editId = editId, error = error, drawer = true, canEdit = canEdit)
</div>
<div class="flex items-center justify-between gap-3 bg-surface-dark px-5 py-3">
    <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0" onclick="drawerClose()">${msg.localize("common.cancel")}</button>
    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="itemForm">${msg.localize("common.save")}</button>
</div>
