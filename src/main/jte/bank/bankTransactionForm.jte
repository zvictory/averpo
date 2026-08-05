@import com.averpo.erp.bank.web.BankTransactionForm
@import com.averpo.erp.contact.domain.Contact
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import java.util.List
@param BankTransactionForm form
@param List<Account> bankAccounts
@param List<Account> lineAccounts
@param List<Contact> contacts
@param java.util.Map<String, String> bankBalances
@param String homeCurrency
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String error = null
@param String classMode = "OFF"
@param List<com.averpo.erp.shared.service.TxnClassService.ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

@template.layout.form(title = msg.lookup("bt.form.title"), closeHref = "/bank-transactions", lang = lang, content = @`
    @template.shared.alert(error = error)

    <%-- Кирим (DEPOSIT) формаси. Чиқим (EXPENSE) энди алоҳида /expenses
         экранида (DEC-033), Ўтказма (TRANSFER) /transfers'да
         (DEC-022) - бу формада тур танлагичи қолмади, hidden DEPOSIT.
         Банк валютаси option'нинг data-currency'сидан: чет валютада курс
         майдони кўринади ва каталогдан prefill бўлади. --%>
    <form id="btForm" method="post" action="/bank-transactions"
          x-data="{fromCur: '', fromBal: ''}"
          x-init="var f = document.querySelector('input[name=bankAccountId]');
                  fromCur = f?.dataset.currency || '${homeCurrency}';
                  fromBal = f?.dataset.balance || ''">
        @template.shared.csrf(csrf = csrf)
        <input type="hidden" name="type" value="DEPOSIT">
        <div class="mb-5 grid grid-cols-1 gap-x-4 gap-y-3 md:grid-cols-2 lg:grid-cols-4">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-2">
                ${msg.localize("bt.form.bank")}
                <%-- Банк валютаси/қолдиғи танланган вариантдан value input
                     data-*'сига кўчади - Alpine занжири ўша ерни ўқийди --%>
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
                <%-- QBO'даги Balance: танланган банк қолдиғи (DEC-012) --%>
                <span x-show="fromBal" x-cloak class="text-sm text-current/60">
                    ${msg.localize("bt.form.balance")}: <b x-text="fromBal"></b></span>
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bt.form.date")}
                <input type="date" name="txnDate" required
                       value="${form.getTxnDate() != null ? form.getTxnDate().toString() : ""}"
                       class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label x-show="fromCur !== '${homeCurrency}'" x-cloak>
                <%-- DEC-065: киритиш йўналиши ёрлиқда аниқ - «1 USD = ? UZS» --%>
                <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize("bt.form.rate")} (1 <span x-text="fromCur"></span> = ? ${homeCurrency}) @template.shared.help(key = "field.exchangeRate", msg = msg)</span>
                <input name="exchangeRate" size="10" inputmode="decimal" data-ratefor="bankAccountId"
                       value="${form.getExchangeRate() != null ? form.getExchangeRate() : ""}"
                       class="mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("bt.form.contact")}
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
            @if("PER_TXN".equals(classMode))
                <%-- PER_TXN (class-tracking.md): битта Йўналиш - сатрларга тарқатилади --%>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("cls.label")}
                    @template.shared.classSelect(classes = classes, msg = msg,
                            name = "classId", selected = form.getClassId(),
                            cls = "mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark")
                </label>
            @endif
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark md:col-span-full">
                ${msg.localize("bt.form.memo")}
                <input name="memo" maxlength="500"
                       value="${form.getMemo() != null ? form.getMemo() : ""}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <%-- Сатрлар жадвали (кирим манба счётлари) --%>
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

        <p class="text-sm text-current/60">
            ${msg.localize("bt.form.currencyNote")}
        </p>
    </form>

    <script>
        // Курс prefill (DEC-029): банк счёти алмашса force=true, сана
        // ўзгарса force=false (фақат автоматик тўлдирилган қиймат
        // янгиланади) - data-autofill нақши; қўлда терилган курс ҳеч
        // қачон устидан ёзилмайди. Home счётда курс бўш хулқи сақланади.
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
                        // Ора вақтда қўлда терилган бўлса устига ёзмаймиз
                        if (!canAutofill(rateInput)) return;
                        if (rate) { rateInput.value = rate; rateInput.dataset.autofill = '1'; }
                        else if (rateInput.dataset.autofill === '1') {
                            // Янги валютада каталог курси йўқ - эскирган
                            // автоматик қиймат тозаланади (нарх нақши)
                            rateInput.value = '';
                            rateInput.dataset.autofill = '';
                        }
                    });
            }
            // Қўлда таҳрир белгини ўчиради (программ ёзув input отмайди)
            document.addEventListener('input', function (e) {
                if (e.target.matches && e.target.matches('input[data-ratefor="bankAccountId"]')) {
                    e.target.dataset.autofill = '';
                }
            });
            field.addEventListener('change', function () { prefillRate(true); });
            if (dateInput) dateInput.addEventListener('change', function () { prefillRate(false); });
        })();
    </script>
`, actions = @`
    <a href="/bank-transactions" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-outline-dark bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface-dark transition hover:border-outline-dark-strong hover:text-on-surface-dark-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-dark active:outline-offset-0">${msg.localize("common.cancel")}</a>
    <div class="ml-auto flex flex-wrap justify-end gap-2.5">
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75" type="submit" form="btForm">
            ${msg.localize("bt.form.save")}</button>
    </div>
`)
