@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.ledger.web.AccountForm
@import com.averpo.erp.shared.domain.Currency
@import java.util.List
@param AccountForm form
@param List<Account> parents
@param List<Currency> currencies
@param List<String> obDetailTypes
@param Msg msg
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String editId = null
@param String error = null

<%-- Arbitr-024: drawer жавоби (HX-Request) - #drawerBody ичига тушади.
     Муваффақиятли POST'да controller HX-Redirect беради (drawer ёпилиб
     рўйхат янгиланади); хатода шу шаблон error билан қайта келади. --%>
<div class="flex items-center justify-between gap-3 bg-surface-dark px-5 py-3.5 font-semibold text-on-surface-dark-strong">
    <b>${editId == null ? msg.localize("accounts.form.titleNew") : msg.localize("accounts.form.titleEdit")}</b>
    <button type="button" class="cursor-pointer rounded-radius border-0 bg-transparent px-2 py-1 text-xl leading-none text-on-surface-dark transition hover:bg-surface-dark-alt hover:text-on-surface-dark-strong" onclick="drawerClose()" aria-label="${msg.lookup("common.cancel")}">✕</button>
</div>
<div class="drawer-body flex-1 overflow-y-auto p-5">
    @template.ledger.accountFormFields(form = form, parents = parents,
            currencies = currencies, obDetailTypes = obDetailTypes,
            msg = msg, csrf = csrf, editId = editId, error = error, drawer = true)
</div>
<div class="flex items-center justify-between gap-3 bg-surface-dark px-5 py-3">
    <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0" onclick="drawerClose()">${msg.localize("common.cancel")}</button>
    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="accForm">${msg.localize("common.save")}</button>
</div>
