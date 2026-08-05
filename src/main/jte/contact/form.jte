@import com.averpo.erp.contact.domain.ContactAddress
@import com.averpo.erp.contact.domain.ContactBankAccount
@import com.averpo.erp.contact.domain.ContactPerson
@import com.averpo.erp.contact.web.ContactForm
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.shared.domain.PaymentTerm
@import java.util.List
@param String kind
@param Boolean isCustomer
@param Boolean isEmployee = false
@param ContactForm form
@param List<Currency> currencies
@param List<PaymentTerm> paymentTerms
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String editId = null
@param String error = null
@param String message = null
<%-- Бўлим маълумотлари фақат таҳрирда келади (controller), яратишда бўш --%>
@param List<ContactAddress> addresses = java.util.List.of()
@param List<ContactPerson> persons = java.util.List.of()
@param List<ContactBankAccount> bankAccounts = java.util.List.of()
<%-- BR-CON-012 (Arbitr-087): POSTED тарихли контакт валютаси қулф --%>
@param Boolean currencyLocked = false
<%-- Arbitr-100: POST redirect'дан кейин очиқ келадиган accordion бўлими --%>
@param String openSection = null

!{String title;
  if (isEmployee != null && isEmployee) {
      title = editId == null ? msg.lookup("contact.form.titleNewEmployee") : msg.lookup("contact.form.titleEditEmployee");
  } else if (isCustomer) {
      title = editId == null ? msg.lookup("contact.form.titleNewCustomer") : msg.lookup("contact.form.titleEditCustomer");
  } else {
      title = editId == null ? msg.lookup("contact.form.titleNewVendor") : msg.lookup("contact.form.titleEditVendor");
  }}

<%-- Arbitr-024: асосий йўл - drawer (HTMX, formDrawer); бу тўлиқ саҳифа
     JS'сиз fallback, тўғридан-тўғри URL ва бўлим формалари redirect'и
     учун сақланади. --%>
@template.layout.form(title = title, closeHref = "/" + kind, lang = lang, content = @`
    @template.contact.formFields(kind = kind, isCustomer = isCustomer, isEmployee = isEmployee, form = form,
            currencies = currencies, paymentTerms = paymentTerms,
            homeCurrency = homeCurrency, msg = msg, csrf = csrf,
            editId = editId, addresses = addresses, persons = persons,
            bankAccounts = bankAccounts, error = error, message = message,
            currencyLocked = currencyLocked, openSection = openSection)
`, actions = @`
    <a href="/${kind}" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="contactForm">${msg.localize("common.save")}</button>
    </div>
`)
