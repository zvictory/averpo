@import com.averpo.erp.contact.domain.AddressType
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
@param List<ContactAddress> addresses = java.util.List.of()
@param List<ContactPerson> persons = java.util.List.of()
@param List<ContactBankAccount> bankAccounts = java.util.List.of()
@param String error = null
@param String message = null
@param Boolean drawer = false
<%-- Arbitr-100: POST redirect'дан кейин очиқ келадиган бўлим
     (addresses/persons/bank) - controller ?open= параметридан узатади.
     null бўлса учаласи ҳам ёпиқ (default). --%>
@param String openSection = null
<%-- BR-CON-012 (Arbitr-087): POSTED тарихли контакт валютаси қулф --%>
@param Boolean currencyLocked = false

<%-- Arbitr-024: форма таначаси - тўлиқ саҳифа (form.jte) ва drawer
     (formDrawer.jte) иккиси ҳам шу partial'ни ишлатади. drawer=true
     бўлса АСОСИЙ форма HTMX орқали submit бўлади; манзил/шахс/банк
     бўлимларининг ўз формалари оддий POST'лигича қолади (улар тўлиқ
     таҳрир саҳифасига redirect қилади - ҳужжатланган чеклов). --%>
    @template.shared.alert(message = message)
    @template.shared.alert(error = error)

    <form id="contactForm" method="post" action="${editId == null ? "/" + kind : "/" + kind + "/" + editId}"
          hx-post="${drawer != null && drawer ? (editId == null ? "/" + kind : "/" + kind + "/" + editId) : null}"
          hx-target="${drawer != null && drawer ? "#drawerBody" : null}"
          hx-swap="${drawer != null && drawer ? "innerHTML" : null}"
          class="${drawer != null && drawer ? "grid gap-[.9rem]" : "grid max-w-[720px] gap-[.9rem] rounded-radius border border-outline bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark"}">
        <%-- Arbitr-053 банд 6: майдон grid'лари 375px'да сиқилмасин - мобилда бир устун
             синфи мобилда бир устунга stack (mobile-first қоидаси). --%>
        @template.shared.csrf(csrf = csrf)

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("contact.form.displayName")}
            <input name="displayName" value="${form.getDisplayName()}" required
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>

        <%-- Ходим учун компания, валюта ва тўлов шартлари қўлланмайди. --%>
        @if(!isEmployee)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("contact.form.companyName")}
                <input name="companyName" value="${form.getCompanyName()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        @endif

        <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-2">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("contact.form.firstName")}
                <input name="firstName" value="${form.getFirstName()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("contact.form.lastName")}
                <input name="lastName" value="${form.getLastName()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-2">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("contact.form.email")}
                <input name="email" type="email" value="${form.getEmail()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("contact.form.phone")}
                <input name="phone" value="${form.getPhone()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        @if(!isEmployee)
            <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-2">
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("contact.form.currency")}
                <%-- Валюта - native select (сурув қарори: 3-10 ёзув) --%>
                <%-- Arbitr-158: «Home валюта» мета-опция ЙЎҚ (QBO паритет -
                     мижоз/таъминотчи конкрет CurrencyRef). Бўш currency →
                     home валюта конкрет default танланади (selCur). nullable
                     модел ва server бўш→home fallback (087) ЎЗГАРМАЙДИ. --%>
                !{var selCur = (form.getCurrency() == null || form.getCurrency().isBlank()) ? homeCurrency : form.getCurrency();}
                @if(currencyLocked != null && currencyLocked)
                    <%-- BR-CON-012 (Arbitr-087): қиймат hidden орқали round-trip
                         бўлади - disabled select submit қилмайди, бўш келса
                         server қиймат→null ўзгариш деб рад этарди; select
                         фақат кўриниш --%>
                    <input type="hidden" name="currency" value="${form.getCurrency()}">
                    <select id="contactCurrency" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" disabled>
                        @for(Currency c : currencies)
                            <option value="${c.getCode()}"
                                    selected="${c.getCode().equals(selCur)}">
                                ${c.getCode()} - ${c.getName()}
                            </option>
                        @endfor
                    </select>
                    <small class="mt-1 block text-current/60">${msg.localize("contact.form.currencyLocked")}</small>
                @else
                    <select name="currency" id="contactCurrency" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                        @for(Currency c : currencies)
                            <option value="${c.getCode()}"
                                    selected="${c.getCode().equals(selCur)}">
                                ${c.getCode()} - ${c.getName()}
                            </option>
                        @endfor
                    </select>
                @endif
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1">${msg.localize("contact.form.paymentTerm")} @template.shared.help(key = "field.paymentTerm", msg = msg)</span>
                <%-- Тўлов шарти - native select (сурув қарори: 4-8 ёзув) --%>
                <select name="paymentTermId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="">${msg.localize("accounts.form.parentNone")}</option>
                    @for(PaymentTerm term : paymentTerms)
                        <option value="${term.getId().toString()}"
                                selected="${term.getId().toString().equals(form.getPaymentTermId())}">
                            ${term.getName()}
                        </option>
                    @endfor
                </select>
                </label>
            </div>
        @endif

        <div class="grid grid-cols-1 gap-[.9rem] min-[521px]:grid-cols-2">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("contact.form.taxId")}
                <input name="taxId" value="${form.getTaxId()}" maxlength="20"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            @if(isCustomer)
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <%-- U5: credit limit контакт валютасида - танловга эргашади --%>
                    <span>${msg.localize("contact.form.creditLimit")}
                    <span id="creditLimitCur" class="text-current/60">${form.getCurrency() != null && !form.getCurrency().isBlank() ? form.getCurrency() : homeCurrency}</span></span>
                    <input name="creditLimit" value="${form.getCreditLimit()}" class="money mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                          >
                </label>
            @endif
            @if(isEmployee != null && isEmployee)
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <%-- Payroll oklad home валютада (payroll фақат home - BR-PYR-001) --%>
                    <span>${msg.localize("contact.form.monthlySalary")}
                    <span class="text-current/60">${homeCurrency}</span></span>
                    <input name="monthlySalary" value="${form.getMonthlySalary()}" class="money mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                          >
                </label>
            @endif
        </div>

        @if(isCustomer)
            <script>
                // U5: контакт валютаси ўзгарса credit limit ёнидаги код янгиланади
                (function () {
                    var select = document.getElementById('contactCurrency');
                    var tag = document.getElementById('creditLimitCur');
                    if (!select || !tag) return;
                    var HOME = "${homeCurrency}";
                    select.addEventListener('change', function () {
                        tag.textContent = select.value || HOME;
                    });
                })();
            </script>
        @endif

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("contact.form.notes")}
            <textarea name="notes" rows="2"
                      class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">${form.getNotes()}</textarea>
        </label>

        @if(editId != null)
            <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-2">
                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${form.isActive()}">
                <input type="hidden" name="_active" value="on">
                ${msg.localize("accounts.form.active")}
            </label>
        @endif
    </form>

    @if(editId == null)
        <p class="mt-4 max-w-[720px] text-sm text-current/60">${msg.localize("contact.section.saveFirst")}</p>
    @else
        <%-- Манзиллар бўлими (Arbitr-100: native accordion, default ёпиқ).
             open атрибути server render'да ?open=addresses'дан келади -
             қўшиш/ўчириш POST'идан кейин шу бўлим очиқ қайтади (JS йўқ). --%>
        <details class="mt-5 max-w-[720px] rounded-radius border border-outline bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark" open="${"addresses".equals(openSection)}">
            <%-- Сон белгиси ёпиқ ҳолатда ҳам мазмун беради («Манзиллар (2)») --%>
            <summary style="font-weight:600;cursor:pointer;user-select:none;padding:.35rem 0;min-height:1.5rem;">${msg.localize("contact.section.addresses")} (${addresses.size()})</summary>
            <div style="display:grid;gap:.9rem;margin-top:.9rem;">
            @if(addresses.isEmpty())
                <p class="text-sm text-current/60">${msg.localize("contact.empty")}</p>
            @else
                @template.shared.table(c = @`
                        <thead>
                        <tr>
                            <th>${msg.localize("contact.col.type")}</th>
                            <th>${msg.localize("contact.address.line1")}</th>
                            <th>${msg.localize("contact.col.default")}</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        @for(ContactAddress address : addresses)
                            <tr>
                                <td>${msg.localize(address.getAddressType().titleKey())}</td>
                                <td>${address.shortText()}</td>
                                <td>${address.isDefaultAddress() ? "✓" : ""}</td>
                                <td class="text-right">
                                    <form method="post" action="/${kind}/${editId}/addresses/${address.getId().toString()}/delete">
                                        @template.shared.csrf(csrf = csrf)
                                        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("contact.delete")}</button>
                                    </form>
                                </td>
                            </tr>
                        @endfor
                        </tbody>
                `)
            @endif
            <form method="post" action="/${kind}/${editId}/addresses"
                  class="grid gap-2.5 border-t border-outline pt-3.5 dark:border-outline-dark">
                @template.shared.csrf(csrf = csrf)
                <div class="grid grid-cols-1 gap-2.5 min-[521px]:grid-cols-[1fr_2fr]">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.col.type")}
                        <select name="type" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                            @for(AddressType type : AddressType.values())
                                <option value="${type.name()}">${msg.localize(type.titleKey())}</option>
                            @endfor
                        </select>
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.address.line1")}
                        <input name="line1" required class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                </div>
                <div class="grid grid-cols-1 gap-2.5 min-[521px]:grid-cols-[2fr_1fr_1fr]">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.address.line2")}
                        <input name="line2" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.address.city")}
                        <input name="city" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.address.region")}
                        <input name="region" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                </div>
                <div class="grid grid-cols-1 items-end gap-2.5 min-[521px]:grid-cols-[1fr_1fr_2fr]">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.address.postal")}
                        <input name="postalCode" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.address.country")}
                        <input name="countryCode" maxlength="2" placeholder="UZ"
                               class="mt-1 w-full uppercase rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 pb-2">
                        <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="defaultAddress" value="true">
                        ${msg.localize("contact.makeDefault")}
                    </label>
                </div>
                <div><button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("contact.add")}</button></div>
            </form>
            </div>
        </details>

        <%-- Масъул шахслар бўлими (Arbitr-100: native accordion, default ёпиқ) --%>
        <details class="mt-5 max-w-[720px] rounded-radius border border-outline bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark" open="${"persons".equals(openSection)}">
            <summary style="font-weight:600;cursor:pointer;user-select:none;padding:.35rem 0;min-height:1.5rem;">${msg.localize("contact.section.persons")} (${persons.size()})</summary>
            <div style="display:grid;gap:.9rem;margin-top:.9rem;">
            @if(persons.isEmpty())
                <p class="text-sm text-current/60">${msg.localize("contact.empty")}</p>
            @else
                @template.shared.table(c = @`
                        <thead>
                        <tr>
                            <th>${msg.localize("contact.person.fullName")}</th>
                            <th>${msg.localize("contact.person.position")}</th>
                            <th>${msg.localize("contact.col.phone")}</th>
                            <th>${msg.localize("contact.col.email")}</th>
                            <th>${msg.localize("contact.col.default")}</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        @for(ContactPerson person : persons)
                            <tr>
                                <td>${person.getFullName()}</td>
                                <td>${person.getPosition()}</td>
                                <td>${person.getPhone()}</td>
                                <td>${person.getEmail()}</td>
                                <td>${person.isPrimary() ? "✓" : ""}</td>
                                <td class="text-right">
                                    <form method="post" action="/${kind}/${editId}/persons/${person.getId().toString()}/delete">
                                        @template.shared.csrf(csrf = csrf)
                                        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("contact.delete")}</button>
                                    </form>
                                </td>
                            </tr>
                        @endfor
                        </tbody>
                `)
            @endif
            <form method="post" action="/${kind}/${editId}/persons"
                  class="grid gap-2.5 border-t border-outline pt-3.5 dark:border-outline-dark">
                @template.shared.csrf(csrf = csrf)
                <div class="grid grid-cols-1 gap-2.5 min-[521px]:grid-cols-2">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.person.fullName")}
                        <input name="fullName" required class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.person.position")}
                        <input name="position" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                </div>
                <div class="grid grid-cols-1 items-end gap-2.5 min-[521px]:grid-cols-3">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.col.phone")}
                        <input name="phone" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.col.email")}
                        <input name="email" type="email" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 pb-2">
                        <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="primary" value="true">
                        ${msg.localize("contact.makeDefault")}
                    </label>
                </div>
                <div><button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("contact.add")}</button></div>
            </form>
            </div>
        </details>

        <%-- Банк реквизитлари бўлими (Arbitr-100: native accordion, default ёпиқ) --%>
        <details class="mt-5 max-w-[720px] rounded-radius border border-outline bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark" open="${"bank".equals(openSection)}">
            <summary style="font-weight:600;cursor:pointer;user-select:none;padding:.35rem 0;min-height:1.5rem;">${msg.localize("contact.section.bank")} (${bankAccounts.size()})</summary>
            <div style="display:grid;gap:.9rem;margin-top:.9rem;">
            @if(bankAccounts.isEmpty())
                <p class="text-sm text-current/60">${msg.localize("contact.empty")}</p>
            @else
                @template.shared.table(c = @`
                        <thead>
                        <tr>
                            <th>${msg.localize("contact.bank.name")}</th>
                            <th>${msg.localize("contact.bank.code")}</th>
                            <th>${msg.localize("contact.bank.number")}</th>
                            <th>${msg.localize("contact.col.currency")}</th>
                            <th>${msg.localize("contact.col.default")}</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        @for(ContactBankAccount account : bankAccounts)
                            <tr>
                                <td>${account.getBankName()}</td>
                                <td>${account.getBankCode()}</td>
                                <td>${account.getAccountNumber()}</td>
                                <td>${account.getCurrency() == null ? "" : account.getCurrency().getCode()}</td>
                                <td>${account.isDefaultAccount() ? "✓" : ""}</td>
                                <td class="text-right">
                                    <form method="post" action="/${kind}/${editId}/bank-accounts/${account.getId().toString()}/delete">
                                        @template.shared.csrf(csrf = csrf)
                                        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit">${msg.localize("contact.delete")}</button>
                                    </form>
                                </td>
                            </tr>
                        @endfor
                        </tbody>
                `)
            @endif
            <form method="post" action="/${kind}/${editId}/bank-accounts"
                  class="grid gap-2.5 border-t border-outline pt-3.5 dark:border-outline-dark">
                @template.shared.csrf(csrf = csrf)
                <div class="grid grid-cols-1 gap-2.5 min-[521px]:grid-cols-[2fr_1fr]">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.bank.name")}
                        <input name="bankName" required class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.bank.code")}
                        <input name="bankCode" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                </div>
                <div class="grid grid-cols-1 items-end gap-2.5 min-[521px]:grid-cols-[2fr_1fr_1fr]">
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.bank.number")}
                        <input name="accountNumber" required class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    </label>
                    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                        ${msg.localize("contact.col.currency")}
                        <select name="currency" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                            <option value="">${msg.localize("contact.form.currencyHome")}</option>
                            @for(Currency c : currencies)
                                <option value="${c.getCode()}">${c.getCode()}</option>
                            @endfor
                        </select>
                    </label>
                    <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-1.5 pb-2">
                        <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="defaultAccount" value="true">
                        ${msg.localize("contact.makeDefault")}
                    </label>
                </div>
                <div><button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("contact.add")}</button></div>
            </form>
            </div>
        </details>
    @endif
