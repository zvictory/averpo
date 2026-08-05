@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.ledger.domain.AccountType
@import com.averpo.erp.ledger.domain.AccountDetailType
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
@param Boolean drawer = false

<%-- DEC-024: форма таначаси - тўлиқ саҳифа (accountForm) ва drawer
     (accountFormDrawer) иккиси ҳам шу partial'ни ишлатади. drawer=true
     бўлса форма HTMX орқали submit бўлади (#drawerBody'га қайта render
     ёки HX-Redirect); false'да оддий POST - JS'сиз fallback шарт. --%>
    @template.shared.alert(error = error)

    <script>
        window.obDetailTypes = [
            @for(String dt : obDetailTypes)"${dt}",@endfor
        ];
        // DEC-161: валютага боғланган турлар (AccountType.isCurrencyDenominated
        // ягона манба) - валюта майдони x-show шу рўйхатдан ўқийди
        window.accountCurrencyTypes = [
            @for(AccountDetailType d : AccountDetailType.values())@if(d.getType().isCurrencyDenominated())"${d.name()}",@endif@endfor
        ];
    </script>
    <form id="accForm" method="post" action="${editId == null ? "/accounts" : "/accounts/" + editId}"
          hx-post="${drawer != null && drawer ? (editId == null ? "/accounts" : "/accounts/" + editId) : null}"
          hx-target="${drawer != null && drawer ? "#drawerBody" : null}"
          hx-swap="${drawer != null && drawer ? "innerHTML" : null}"
          x-data="{dt: '${form.getDetailType()}', cur: '${form.getCurrency()}'}"
          x-effect="if (!window.accountCurrencyTypes.includes(dt)) cur = ''"
          class="${drawer != null && drawer ? "grid gap-[.9rem]" : "grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface p-5 dark:border-outline-dark dark:bg-surface-dark"}">
        @template.shared.csrf(csrf = csrf)
        <%-- DEC-024: QBO'дек икки устунли жуфтлар (мобилда битта устун) --%>
        <div class="grid grid-cols-1 gap-[.9rem] md:grid-cols-2">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("accounts.form.name")}
                <input name="name" value="${form.getName()}" required
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("accounts.form.code")}
                <input name="code" value="${form.getCode()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <div class="grid grid-cols-1 gap-[.9rem] md:grid-cols-2">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("accounts.form.detailType")}
                <select name="detailType" required x-model="dt"
                        class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="">${msg.localize("accounts.form.select")}</option>
                    @for(AccountType type : AccountType.values())
                        <optgroup label="${msg.lookup(type.titleKey())}">
                            @for(AccountDetailType detail : AccountDetailType.forType(type))
                                <option value="${detail.name()}"
                                        selected="${detail.name().equals(form.getDetailType())}">
                                    ${msg.localize(detail.titleKey())}
                                </option>
                            @endfor
                        </optgroup>
                    @endfor
                </select>
            </label>
            <label x-show="window.accountCurrencyTypes.includes(dt)" x-cloak class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("accounts.form.currency")}
                <%-- Валюта - фақат валютага боғланган турда (DEC-161 x-show).
                     :disabled - валютага боғланмаган турда select ЎЧИРИЛАДИ:
                     disabled control submit ҚИЛИНМАЙДИ, шунга x-show яширин
                     select DOM қиймати stale қолса ҳам юборилмайди (Income'га
                     валюта хатоси чиқмайди). x-effect эса Alpine cur'ни тозалайди
                     (OB курс x-show='cur!==""' мантиғи учун) --%>
                <select name="currency" x-model="cur" :disabled="!window.accountCurrencyTypes.includes(dt)" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                    <option value="">${msg.localize("accounts.form.parentNone")}</option>
                    @for(Currency c : currencies)
                        <option value="${c.getCode()}"
                                selected="${c.getCode().equals(form.getCurrency())}">
                            ${c.getCode()} - ${c.getName()}
                        </option>
                    @endfor
                </select>
            </label>
        </div>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("accounts.form.description")}
            <input name="description" value="${form.getDescription()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("accounts.form.parent")}
            <%-- DEC-014: QBO tree. Ўзи рўйхатдан чиқарилади (авлодларини
                 server BR-COA-003 ушлайди); ота сифатида группа счётлар ҳам
                 танланади - disableNonPostable=false --%>
            !{var parentOptions = parents.stream()
                    .filter(p -> editId == null || !p.getId().toString().equals(editId))
                    .toList();}
            @template.shared.combobox(name = "parentId", msg = msg,
                    value = form.getParentId(),
                    placeholder = msg.lookup("accounts.form.parentNone"),
                    cls = "mt-1 w-full",
                    options = @`
                        @template.shared.comboOption(value = "", label = msg.lookup("accounts.form.parentNone"))
                        @template.shared.comboAccountOptions(accounts = parentOptions, msg = msg,
                                selected = form.getParentId(), disableNonPostable = false)
                    `)
        </label>

        @if(editId == null)
            <div class="grid gap-3.5 border-t border-outline pt-3.5 dark:border-outline-dark" x-show="window.obDetailTypes.includes(dt)">
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    <span class="inline-flex items-center gap-1">${msg.localize("accounts.form.openingBalance")} @template.shared.help(key = "field.openingBalance", msg = msg)</span>
                    <input name="openingBalance" value="${form.getOpeningBalance()}"
                           class="money mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
                          >
                </label>
                <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("accounts.form.openingBalanceDate")}
                    <input type="date" name="openingBalanceDate" value="${form.getOpeningBalanceDate()}"
                           class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                </label>
                <label x-show="cur !== ''">
                    ${msg.localize("accounts.form.openingBalanceRate")}
                    <input name="openingBalanceRate" value="${form.getOpeningBalanceRate()}"
                           inputmode="decimal"
                           class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                </label>
            </div>
        @endif

        <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-2">
            <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="postable" value="true" checked="${form.isPostable()}">
            <input type="hidden" name="_postable" value="on">
            ${msg.localize("accounts.form.postable")}
        </label>

        @if(editId != null)
            <label class="flex items-center text-sm font-medium text-on-surface dark:text-on-surface-dark gap-2">
                <input type="checkbox" class="size-4 accent-primary dark:accent-primary-dark" name="active" value="true" checked="${form.isActive()}">
                <input type="hidden" name="_active" value="on">
                ${msg.localize("accounts.form.active")}
            </label>
        @endif
    </form>

    <script>
        // Курс prefill (4-босқич): OB валютаси/санаси ўзгарганда каталогдан
        // курс олинади - фақат бўш майдон тўлдирилади (home'га курс йўқ,
        // lookup бўш қайтаради)
        (function () {
            var currencySelect = document.querySelector('select[name="currency"]');
            var dateInput = document.querySelector('input[name="openingBalanceDate"]');
            var rateInput = document.querySelector('input[name="openingBalanceRate"]');
            if (!currencySelect || !rateInput) return;
            function prefill() {
                var code = currencySelect.value;
                if (!code || rateInput.value.trim() !== '') return;
                var date = dateInput && dateInput.value ? dateInput.value : '';
                fetch('/exchange-rates/lookup?currency=' + encodeURIComponent(code)
                        + '&date=' + encodeURIComponent(date))
                    .then(function (r) { return r.ok ? r.text() : ''; })
                    .then(function (rate) { if (rate) rateInput.value = rate; });
            }
            currencySelect.addEventListener('change', prefill);
            if (dateInput) dateInput.addEventListener('change', prefill);
        })();
    </script>
