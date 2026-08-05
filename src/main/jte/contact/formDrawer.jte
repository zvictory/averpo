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
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String editId = null
@param String error = null
@param List<ContactAddress> addresses = java.util.List.of()
@param List<ContactPerson> persons = java.util.List.of()
@param List<ContactBankAccount> bankAccounts = java.util.List.of()
<%-- BR-CON-012 (DEC-087): POSTED тарихли контакт валютаси қулф --%>
@param Boolean currencyLocked = false
<%-- DEC-100: accordion очиқ бўлими (drawer'да бўлим формалари
     редирект қилмайди - тўлиқлик учун узатилади) --%>
@param String openSection = null

!{String title;
  if (isEmployee != null && isEmployee) {
      title = editId == null ? msg.lookup("contact.form.titleNewEmployee") : msg.lookup("contact.form.titleEditEmployee");
  } else if (isCustomer) {
      title = editId == null ? msg.lookup("contact.form.titleNewCustomer") : msg.lookup("contact.form.titleEditCustomer");
  } else {
      title = editId == null ? msg.lookup("contact.form.titleNewVendor") : msg.lookup("contact.form.titleEditVendor");
  }}

<%-- DEC-024: drawer жавоби (HX-Request). Манзил/шахс/банк бўлимлари
     drawer'да ҳам кўринади; уларнинг ўз формалари оддий POST - submit
     тўлиқ таҳрир саҳифасига олиб чиқади (ҳужжатланган чеклов). --%>
<div class="flex items-center justify-between gap-3 bg-surface-dark px-5 py-3.5 font-semibold text-on-surface-dark-strong">
    <b>${title}</b>
    <button type="button" class="cursor-pointer rounded-radius border-0 bg-transparent px-2 py-1 text-xl leading-none text-on-surface-dark transition hover:bg-surface-dark-alt hover:text-on-surface-dark-strong" onclick="drawerClose()" aria-label="${msg.lookup("common.cancel")}">✕</button>
</div>
<div class="drawer-body flex-1 overflow-y-auto p-5">
    @template.contact.formFields(kind = kind, isCustomer = isCustomer, isEmployee = isEmployee, form = form,
            currencies = currencies, paymentTerms = paymentTerms,
            homeCurrency = homeCurrency, msg = msg, csrf = csrf,
            editId = editId, addresses = addresses, persons = persons,
            bankAccounts = bankAccounts, error = error, drawer = true,
            currencyLocked = currencyLocked, openSection = openSection)
</div>
<div class="flex items-center justify-between gap-3 bg-surface-dark px-5 py-3">
    <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0" onclick="drawerClose()">${msg.localize("common.cancel")}</button>
    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="contactForm">${msg.localize("common.save")}</button>
</div>
