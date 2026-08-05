@import com.averpo.erp.bank.web.BankTransactionForm
@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.shared.domain.PaymentMethod
@import java.util.List
@param BankTransactionForm form
@param List<Account> bankAccounts
@param List<Account> lineAccounts
@param List<Contact> contacts
@param List<PaymentMethod> paymentMethods
@param java.util.Map<String, String> bankBalances
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<com.averpo.erp.shared.service.TxnClassService.ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

<%-- Чиқим (Expense) - QBO /app/expense тартиби (DEC-033): Payee |
     Тўлов счёти (жонли Balance) | катта жонли AMOUNT | курс | сана |
     тўлов усули | Ref no | сатрлар | Total (ҳужжат валютаси + home) |
     memo. Ёзувчи мантиқ ўша BankTransactionService.expense - проводка
     ўзгармаган. --%>
@template.layout.form(title = msg.lookup("expense.form.title"), closeHref = "/expenses", lang = lang, content = @`
    @template.shared.alert(error = error)

    <form id="expForm" method="post" action="/expenses"
          x-data="{fromCur: '', fromBal: ''}"
          x-init="var f = document.querySelector('input[name=bankAccountId]');
                  fromCur = f?.dataset.currency || '${homeCurrency}';
                  fromBal = f?.dataset.balance || ''">
        @template.shared.csrf(csrf = csrf)
        <input type="hidden" name="type" value="EXPENSE">

        <%-- QBO юқори қатор: Payee | Payment account (+Balance) | AMOUNT --%>
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("expense.form.payee")}
                @template.shared.combobox(name = "contactId", msg = msg,
                        value = form.getContactId(),
                        placeholder = msg.lookup("bt.row.noContact"),
                        cls = "mt-1 w-full",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("bt.row.noContact"))
                            @for(Contact contact : contacts)
                                @template.shared.comboOption(value = contact.getId().toString(),
                                        label = contact.getDisplayName(),
                                        selected = contact.getId().toString().equals(form.getContactId()))
                            @endfor
                        `)
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("expense.form.paymentAccount")}
                <%-- Банк валютаси/қолдиғи value input data-*'сида - Alpine
                     занжири (fromCur/fromBal) ва курс prefill ўша ерни ўқийди --%>
                !{var selBank = bankAccounts.stream().filter(a -> a.getId().toString().equals(form.getBankAccountId())).findFirst().orElse(null);}
                @template.shared.combobox(name = "bankAccountId", msg = msg,
                        value = form.getBankAccountId(),
                        placeholder = msg.lookup("bt.form.selectBank"),
                        required = true,
                        cls = "mt-1 w-full",
                        addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                        addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                        dataCurrency = selBank != null ? (selBank.getCurrency() != null ? selBank.getCurrency().getCode() : homeCurrency) : null,
                        dataBalance = selBank != null ? bankBalances.get(selBank.getId().toString()) : null,
                        onchange = "fromCur = $event.target.dataset.currency || '" + homeCurrency + "'; fromBal = $event.target.dataset.balance || ''",
                        options = @`
                            @template.shared.comboOption(value = "", label = msg.lookup("bt.form.selectBank"))
                            @for(Account account : bankAccounts)
                                @template.shared.comboOption(value = account.getId().toString(),
                                        label = (account.getCode() != null ? account.getCode() + " " : "") + account.getName() + " - " + (account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency),
                                        selected = account.getId().toString().equals(form.getBankAccountId()),
                                        dataCurrency = account.getCurrency() != null ? account.getCurrency().getCode() : homeCurrency,
                                        dataBalance = bankBalances.get(account.getId().toString()))
                            @endfor
                        `)
                <span x-show="fromBal" x-cloak class="text-sm text-current/60">
                    ${msg.localize("bt.form.balance")}: <b x-text="fromBal"></b></span>
            </label>
            <%-- QBO катта AMOUNT - сатрлардан жонли йиғилади (кўрсатиш) --%>
            <div style="margin-left:auto;text-align:right;">
                <div class="text-xs uppercase tracking-wider text-current/60">${msg.localize("expense.form.amount")}</div>
                <div style="font-size:1.9rem;font-weight:700;white-space:nowrap;">
                    <span id="expAmountLive">0.00</span> <span x-text="fromCur" class="text-lg text-current/60"></span>
                </div>
            </div>

        <%-- Иккинчи қатор: курс (чет валютада) | сана | тўлов усули | Ref no --%>
            <label x-show="fromCur !== '${homeCurrency}'" x-cloak>
                <%-- DEC-065: киритиш йўналиши ёрлиқда аниқ - «1 USD = ? UZS» --%>
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.rate")} (1 <span x-text="fromCur"></span> = ? ${homeCurrency}) @template.shared.help(key = "field.exchangeRate", msg = msg)</span>
                <input name="exchangeRate" size="10" inputmode="decimal" data-ratefor="bankAccountId"
                       value="${form.getExchangeRate() != null ? form.getExchangeRate() : ""}"
                       class="mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("expense.form.date")}
                <input type="date" name="txnDate" required
                       value="${form.getTxnDate() != null ? form.getTxnDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("expense.form.method")}
                <%-- Тўлов усули - native select (сурув қарори: 3-5 ёзув,
                     Penguin select канони кифоя) --%>
                <select name="paymentMethodId" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"
                       >
                    <option value="">${msg.localize("bt.row.noContact")}</option>
                    @for(PaymentMethod method : paymentMethods)
                        <option value="${method.getId().toString()}"
                                selected="${method.getId().toString().equals(form.getPaymentMethodId())}">
                            ${method.getName()}
                        </option>
                    @endfor
                </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("expense.form.refNo")}
                <input name="refNo" maxlength="30" size="14"
                       value="${form.getRefNo() != null ? form.getRefNo() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            @if("PER_TXN".equals(classMode))
                <%-- PER_TXN (class-tracking.md): битта Йўналиш - сатрларга тарқатилади --%>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("cls.label")}
                    @template.shared.classSelect(classes = classes, msg = msg,
                            name = "classId", selected = form.getClassId(),
                            cls = "mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark")
                </label>
            @endif
        </div>

        <%-- Сатрлар (QBO CATEGORY/DESCRIPTION/AMOUNT) - generic partial
             қайта ишлатилади, счётлар systemManaged'сиз (X-012) --%>
        <div>
            @template.shared.table(cls = "min-w-[820px]", wrap = "mb-3", c = @`
                <thead>
                <tr>
                    <th class="w-10">#</th>
                    <th class="min-w-[200px]">${msg.localize("bt.row.account")}</th>
                    <th>${msg.localize("bt.row.amount")} <span x-text="fromCur" class="font-normal"></span></th>
                    <th>${msg.localize("bt.row.contact")}</th>
                    @if("PER_LINE".equals(classMode))
                        <th>${msg.localize("cls.label")}</th>
                    @endif
                    <th>${msg.localize("bt.row.memo")}</th>
                    <th class="w-10"></th>
                </tr>
                </thead>
                <tbody id="lineRows">
                @for(var i = 0; i < form.getLines().size(); i++)
                    @template.bank.bankTxnLineRow(index = i, lineAccounts = lineAccounts,
                            contacts = contacts, msg = msg, line = form.getLines().get(i),
                            classMode = classMode, classes = classes, canEdit = canEdit)
                @endfor
                </tbody>
            `)
            <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark mb-4"
                    hx-get="/bank-transactions/line-row"
                    hx-vals='js:{index: Math.max(0, ...[...document.querySelectorAll("#lineRows tr")].map(r => 1 + Number(r.dataset.index || 0)))}'
                    hx-target="#lineRows" hx-swap="beforeend">${msg.localize("bt.form.addRow")}</button>
        </div>

        <%-- Total: ҳужжат валютасида, чет валютада home'да ҳам (қоида: пул доим валютаси билан) --%>
        <div style="display:flex;justify-content:flex-end;margin-bottom:1rem;">
            <div class="min-w-[240px] rounded-radius border border-outline bg-surface-alt px-4 py-3 text-right dark:border-outline-dark dark:bg-surface-dark-alt">
                <div style="display:flex;justify-content:space-between;gap:1.5rem;">
                    <b>${msg.localize("tax.total")}</b>
                    <span style="white-space:nowrap;"><span id="expTotalLive">0.00</span> <span x-text="fromCur"></span></span>
                </div>
                <div
                     x-show="fromCur !== '${homeCurrency}'" x-cloak class="flex justify-between gap-6 text-sm text-current/60">
                    <span>${msg.localize("tax.total")} (${homeCurrency})</span>
                    <span id="expTotalHomeLive" style="white-space:nowrap;">0.00</span>
                </div>
            </div>
        </div>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark max-w-[640px]">
            ${msg.localize("bt.form.memo")}
            <textarea name="memo" rows="3" maxlength="500"
                      class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">${form.getMemo() != null ? form.getMemo() : ""}</textarea>
        </label>
    </form>

    <script>
        // Курс prefill (DEC-029 нақши - bankTransactionForm билан бир
        // хил): счёт алмашса force=true, сана ўзгарса фақат автоматик
        // қиймат янгиланади; қўлда терилган курс устидан ёзилмайди.
        (function () {
            var HOME = "${homeCurrency}";
            var field = document.querySelector('input[name=bankAccountId]');
            var dateInput = document.querySelector('input[name="txnDate"]');
            if (!field) return;
            function rateInputEl() { return document.querySelector('input[data-ratefor="bankAccountId"]'); }
            function canAutofill(input) {
                return input.value.trim() === '' || input.dataset.autofill === '1';
            }
            function prefillRate(force) {
                var rateInput = rateInputEl();
                if (!rateInput) return;
                var code = field.dataset.currency || HOME;
                if (code === HOME) { rateInput.value = ''; rateInput.dataset.autofill = ''; return; }
                if (!canAutofill(rateInput)) return;
                if (!force && rateInput.value.trim() === '') return;
                var date = dateInput ? dateInput.value : '';
                fetch('/exchange-rates/lookup?currency=' + encodeURIComponent(code)
                        + '&date=' + encodeURIComponent(date))
                    .then(function (r) { return r.ok ? r.text() : ''; })
                    .then(function (rate) {
                        if (!canAutofill(rateInput)) return;
                        if (rate) { rateInput.value = rate; rateInput.dataset.autofill = '1'; }
                        else if (rateInput.dataset.autofill === '1') {
                            rateInput.value = '';
                            rateInput.dataset.autofill = '';
                        }
                    });
            }
            document.addEventListener('input', function (e) {
                if (e.target.matches && e.target.matches('input[data-ratefor="bankAccountId"]')) {
                    e.target.dataset.autofill = '';
                }
            });
            field.addEventListener('change', function () { prefillRate(true); });
            if (dateInput) dateInput.addEventListener('change', function () { prefillRate(false); });
        })();

        // Жонли AMOUNT/Total (BA-004 нақши): сатр суммалари йиғилади -
        // фақат кўрсатиш, server ҳисоби/BR'лар ўз жойида. Формат Fmt.money
        // кўзгуси: 2 хона, нуқта каср, NBSP минг ажратгич. Home жами =
        // жами × курс - фақат кўрсатиш учун тахмин (аниқ base server'да).
        (function () {
            var body = document.getElementById('lineRows');
            if (!body) return;
            function num(value) {
                var raw = (value || '').replace(/\s/g, '').replace(',', '.');
                var n = parseFloat(raw);
                return isNaN(n) ? 0 : n;
            }
            function fmt(n) {
                var sign = n < 0 ? '-' : '';
                var parts = Math.abs(n).toFixed(2).split('.');
                return sign + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, String.fromCharCode(160)) + '.' + parts[1];
            }
            function recompute() {
                var total = 0;
                body.querySelectorAll('tr.line').forEach(function (row) {
                    var amount = row.querySelector('input[name$=".amount"]');
                    if (amount) total += num(amount.value);
                });
                document.getElementById('expAmountLive').textContent = fmt(total);
                document.getElementById('expTotalLive').textContent = fmt(total);
                var rateInput = document.querySelector('input[data-ratefor="bankAccountId"]');
                var rate = rateInput && rateInput.value.trim() !== '' ? num(rateInput.value) : 1;
                document.getElementById('expTotalHomeLive').textContent = fmt(total * rate);
            }
            document.addEventListener('input', function (e) {
                if (!e.target.name) return;
                if (e.target.name.endsWith('.amount') || e.target.name === 'exchangeRate') recompute();
            });
            // Сатр ўчирилганда ҳам янгиланади (✕ тугмаси click'дан кейин)
            document.addEventListener('click', function (e) {
                if (e.target.closest && e.target.closest('button[aria-label]')) setTimeout(recompute, 0);
            });
            recompute();
        })();
    </script>
`, actions = @`
    <a href="/expenses" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="expForm">
            ${msg.localize("bt.form.save")}</button>
    </div>
`)
