@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.purchase.domain.Bill
@import com.averpo.erp.purchase.web.BillPaymentForm
@import com.averpo.erp.shared.domain.Currency
@import java.util.List
@param BillPaymentForm form
@param List<Contact> vendors
@param List<Account> bankAccounts
@param List<Currency> currencies
@param List<Bill> openBills
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("pay.form.title"), closeHref = "/payments", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="payForm" method="post" action="/payments">
        @template.shared.csrf(csrf = csrf)
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("pay.form.vendor")}
                <%-- Танлов ўзгарганда очиқ bill'лар HTMX билан янгиланади -
                     hx-* value input'да (change ундан bubbles отилади) --%>
                @template.shared.combobox(name = "vendorId", msg = msg,
                        value = form.getVendorId(),
                        placeholder = msg.lookup("bill.form.selectVendor"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/vendors/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addVendor") : null,
                        hxGet = "/payments/open-bills",
                        hxTarget = "#openBillRows",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("bill.form.selectVendor"))
                            @for(Contact vendor : vendors)
                                @template.shared.comboOption(value = vendor.getId().toString(),
                                        label = vendor.getDisplayName(),
                                        selected = vendor.getId().toString().equals(form.getVendorId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("pay.form.date")}
                <input type="date" name="paymentDate" required
                       value="${form.getPaymentDate() != null ? form.getPaymentDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("pay.form.bankAccount")}
                <%-- Arbitr-014: QBO tree - банк счёти валютаси кўринади;
                     id payBank - қуйидаги валюта филтри JS'и учун --%>
                @template.shared.combobox(name = "bankAccountId", msg = msg,
                        value = form.getBankAccountId(),
                        placeholder = msg.lookup("pay.form.selectBank"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                        id = "payBank",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("pay.form.selectBank"))
                            @template.shared.comboAccountOptions(accounts = bankAccounts, msg = msg,
                                    selected = form.getBankAccountId())
                        `)
            </label>
            <%-- Arbitr-097: валюта select + курс майдони - shared.rateBlock
                 (prefill + ориентация). Тўлов формасида жадвал жамиси йўқ -
                 home жами қисми ишламайди (rate-block.js graceful). --%>
            @template.shared.rateBlock(currencies = currencies, selected = form.getCurrency(),
                    exchangeRate = form.getExchangeRate(), homeCurrency = homeCurrency,
                    dateInputName = "paymentDate", labelKey = "bill.form.currency",
                    rateKey = "bill.form.rate", currencyId = "payCurrency", msg = msg)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                <span>${msg.localize("pay.form.total")} <span data-curtag class="text-current/60">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></span>
                <input name="totalAmount" class="money mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" required size="14"
                       value="${form.getTotalAmount() != null ? form.getTotalAmount() : ""}"
                      >
                <small class="block text-current/60">${msg.localize("pay.form.totalNote")}</small>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("bill.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("pay.form.openBills")}</h3>
        @template.shared.table(cls = "min-w-[760px]", wrap = "mb-3", c = @`
            <thead>
            <tr>
                <th>${msg.localize("bill.col.number")}</th>
                <th>${msg.localize("bill.col.date")}</th>
                <th>${msg.localize("bill.col.dueDate")}</th>
                <th class="text-right">${msg.localize("bill.col.total")}</th>
                <th class="text-right">${msg.localize("bill.col.balance")}</th>
                <%-- U2: тақсимот суммалари тўлов валютасида киритилади --%>
                <th class="min-w-[130px]">${msg.localize("pay.form.allocate")} <span data-curtag class="font-normal">${form.getCurrency() != null ? form.getCurrency() : homeCurrency}</span></th>
            </tr>
            </thead>
            <tbody id="openBillRows">
                @template.purchase.paymentOpenBills(openBills = openBills, msg = msg)
            </tbody>
        `)
        <p class="text-sm text-current/60">${msg.localize("pay.form.advanceNote")}</p>
    </form>

    <script>
        <%-- Arbitr-097: курс prefill + ориентация - rate-block.js
             компонентида (эски IIFE ЎЧИРИЛДИ; банк филтр ва курс-view
             қуйида, банк филтр payCurrency id билан ишлайверади). --%>

        // Банк счёти валюта филтри (Arbitr-070/Nargiza-044): combobox тўлов
        // валютасига мос BANK счётларгагина рухсат беради - мос эмас счёт
        // танлаб тўлиқ форма тўлдиргач рад олиш олдини олади. Фақат UX -
        // ҳақиқат манбаи server гарови (BR-PAY-002). invoicePaymentForm
        // филтрининг кўзгуси (у ерда UNDEPOSITED истисноси ҳам бор).
        // Ташқи филтр li.hidden ишлатади - combobox қидируви (style.display)
        // билан тўқнашмайди; ота жилдлар combobox рўйхатида умуман йўқ.
        (function () {
            var HOME = "${homeCurrency}";
            var curSel = document.getElementById('payCurrency');
            var accField = document.getElementById('payBank');
            if (!curSel || !accField) return;
            var box = accField.closest('[data-combobox]');
            function applyFilter() {
                var code = (curSel.value || HOME).trim().toUpperCase();
                var cleared = false;
                var lis = box.querySelectorAll('[role="option"][data-value]');
                Array.prototype.forEach.call(lis, function (li) {
                    if (!li.dataset.value) return;
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
    <a href="/payments" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="payForm">
            ${msg.localize("pay.form.save")}</button>
    </div>
`)
