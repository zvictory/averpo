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
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String editId = null
@param String error = null

<%-- DEC-024: асосий йўл - drawer (HTMX, accountFormDrawer); бу тўлиқ
     саҳифа JS'сиз fallback ва тўғридан-тўғри URL учун сақланади. --%>
@template.layout.form(title = editId == null ? msg.lookup("accounts.form.titleNew") : msg.lookup("accounts.form.titleEdit"), closeHref = "/accounts", lang = lang, content = @`
    @template.ledger.accountFormFields(form = form, parents = parents,
            currencies = currencies, obDetailTypes = obDetailTypes,
            msg = msg, csrf = csrf, editId = editId, error = error)
`, actions = @`
    <a href="/accounts" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="accForm">${msg.localize("common.save")}</button>
    </div>
`)
