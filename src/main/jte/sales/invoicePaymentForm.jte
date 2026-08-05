@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.sales.domain.Invoice
@import com.averpo.erp.sales.web.InvoicePaymentForm
@import com.averpo.erp.shared.domain.Currency
@import java.util.List
@param InvoicePaymentForm form
@param List<Contact> customers
@param List<Account> depositAccounts
@param List<Currency> currencies
@param List<Invoice> openInvoices
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("rcpt.form.title"), closeHref = "/invoice-payments", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="rcptForm" method="post" action="/invoice-payments">
        @template.shared.csrf(csrf = csrf)
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("rcpt.form.customer")}
                <%-- Танлов ўзгарганда очиқ invoice'лар HTMX билан янгиланади --%>
                @template.shared.combobox(name = "customerId", msg = msg,
                        value = form.getCustomerId(),
                        placeholder = msg.lookup("rcpt.form.selectCustomer"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/customers/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addCustomer") : null,
                        hxGet = "/invoice-payments/open-invoices",
                        hxTarget = "#openInvoiceRows",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("rcpt.form.selectCustomer"))
                            @for(Contact customer : customers)
                                @template.shared.comboOption(value = customer.getId().toString(),
                                        label = customer.getDisplayName(),
                                        selected = customer.getId().toString().equals(form.getCustomerId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("rcpt.form.date")}
                <input type="date" name="paymentDate" required
                       value="${form.getPaymentDate() != null ? form.getPaymentDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span class="inline-flex items-center gap-1">${msg.localize("rcpt.form.depositAccount")} @template.shared.help(key = "field.depositAccount", msg = msg)</span>
                <%-- Arbitr-014: QBO tree - счёт валютаси кўринади; id
                     rcptDeposit - қуйидаги валюта филтри JS'и учун --%>
                @template.shared.combobox(name = "depositAccountId", msg = msg,
                        value = form.getDepositAccountId(),
                        placeholder = msg.lookup("rcpt.form.selectDeposit"),
                        required = true,
                        cls = "mt-1 w-full",
                        id = "rcptDeposit",
                        addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("rcpt.form.selectDeposit"))
                            @template.shared.comboAccountOptions(accounts = depositAccounts, msg = msg,
                                    selected = form.getDepositAccountId())
                        `)
            </label>
            <%-- Arbitr-097: валюта select + курс майдони - shared.rateBlock
                 (prefill + ориентация). Тўлов формасида жадвал жамиси йўқ. --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "paymentDate", labelKey = "rcpt.form.currency",
                    rateKey = "rcpt.form.rate", currencyId = "rcptCurrency", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span>${msg.localize("rcpt.form.total")} <span data-curtag class="text-current/60">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></span>
                <input name="totalAmount" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" required size="14"
                       value="${form.getTotalAmount() != null ? form.getTotalAmount() : ""}"
                      >
                <small class="block text-current/60">${msg.localize("rcpt.form.totalNote")}</small>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("rcpt.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("rcpt.form.openInvoices")}</h3>
        @template.shared.table(cls = "min-w-[760px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th>${msg.localize("sinv.col.number")}</th>
                <th>${msg.localize("sinv.col.date")}</th>
                <th>${msg.localize("sinv.col.dueDate")}</th>
                <th class="text-right">${msg.localize("sinv.col.total")}</th>
                <th class="text-right">${msg.localize("sinv.col.balance")}</th>
                <th class="min-w-[130px]">${msg.localize("rcpt.form.allocate")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
            </tr>
            </thead>
            <tbody id="openInvoiceRows">
                @template.sales.invoicePaymentOpenInvoices(openInvoices = openInvoices, msg = msg)
            </tbody>
        `)
        <p class="text-sm text-current/60">${msg.localize("rcpt.form.advanceNote")}</p>
    </form>

    <script>
        <%-- Arbitr-097: курс prefill + ориентация - rate-block.js
             компонентида (эски IIFE ЎЧИРИЛДИ; счёт филтр rcptCurrency
             id билан ишлайверади). --%>

        // Счёт валюта филтри (Arbitr-070/Nargiza-044): combobox тўлов
        // валютасига мос BANK счётларгагина рухсат беради - мос эмас счёт
        // танлаб тўлиқ форма тўлдиргач рад олиш олдини олади. UNDEPOSITED
        // клиринг чўнтаги (data-undep) ДОИМ қолади - чет валюта тўлов
        // undeposited орқали қабул қилинади. Фақат UX - ҳақиқат манбаи
        // server гарови (BR-RCPT-002). Ташқи филтр li.hidden ишлатади -
        // combobox қидируви (style.display) билан тўқнашмайди; ота
        // жилдлар combobox рўйхатида умуман йўқ.
        (function () {
            var HOME = "${homeCurrency}";
            var curSel = document.getElementById('rcptCurrency');
            var accField = document.getElementById('rcptDeposit');
            if (!curSel || !accField) return;
            var box = accField.closest('[data-combobox]');
            function applyFilter() {
                var code = (curSel.value || HOME).trim().toUpperCase();
                var cleared = false;
                var lis = box.querySelectorAll('[role="option"][data-value]');
                Array.prototype.forEach.call(lis, function (li) {
                    if (!li.dataset.value || li.dataset.undep === '1') return;
                    var match = (li.dataset.cur || HOME) === code;
                    li.hidden = !match;
                    if (!match && accField.value === li.dataset.value) cleared = true;
                });
                // Гуруҳ сарлавҳаси остида кўринар банд қолмаса у ҳам яширилади
                var header = null, headerHasVisible = false;
                function flushHeader() { if (header) header.hidden = !headerHasVisible; }
                Array.prototype.forEach.call(
                        box.querySelectorAll('[role="presentation"], [role="option"][data-value]'),
                        function (el) {
                            if (el.getAttribute('role') === 'presentation') {
                                flushHeader();
                                header = el;
                                headerHasVisible = false;
                            } else if (!el.hidden && el.dataset.value) {
                                headerHasVisible = true;
                            }
                        });
                flushHeader();
                if (cleared) {
                    accField.value = '';
                    accField.dispatchEvent(new Event('change', { bubbles: true }));
                }
            }
            curSel.addEventListener('change', applyFilter);
            applyFilter();
        })();

        <%-- Arbitr-097: курс майдони кўрсат/яшир - rate-block.js
             компонентида (эски Arbitr-088 view IIFE ЎЧИРИЛДИ). --%>
    </script>
`, actions = @`
    <a href="/invoice-payments" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="rcptForm">
            ${msg.localize("rcpt.form.save")}</button>
    </div>
`)
