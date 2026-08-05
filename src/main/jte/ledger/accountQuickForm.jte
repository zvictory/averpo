@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.AccountType
@import com.averpo.erp.ledger.domain.AccountDetailType
@import com.averpo.erp.shared.domain.Currency
@import java.util.List
@param Msg msg
@param List<Currency> currencies
@param org.springframework.security.web.csrf.CsrfToken csrf = null

<%-- Combobox quick-add модал fragment'и (spec: combobox.md):
     ном + detail type (группаланган select - accountFormFields кўзгуси);
     код/ота/валюта/OB кейин тўлиқ формада. pgCombo (penguin-combobox.js)
     submit'ни ушлаб JSON {id,label} кутади; BR хато [data-combo-error]'да. --%>
<%-- DEC-161: валютага боғланган турлар (AccountType.isCurrencyDenominated
     ягона манба) - валюта майдони x-show шу рўйхатдан ўқийди --%>
<script>
    window.accountCurrencyTypes = [
        @for(AccountDetailType d : AccountDetailType.values())@if(d.getType().isCurrencyDenominated())"${d.name()}",@endif@endfor
    ];
</script>
<form method="post" action="/accounts/quick" data-combo-quick
      data-combo-title="${msg.lookup("combo.title.account")}"
      x-data="{dt: '', cur: ''}"
      x-effect="if (!window.accountCurrencyTypes.includes(dt)) cur = ''"
      style="display:grid;gap:.9rem;">
    @template.shared.csrf(csrf = csrf)
    <p class="m-0 rounded-radius border border-danger/30 bg-danger/10 px-3 py-2.5 text-danger" data-combo-error hidden></p>
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
        ${msg.localize("accounts.form.name")}
        <input name="name" required
               class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    </label>
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
        ${msg.localize("accounts.form.detailType")}
        <select name="detailType" required x-model="dt"
                class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <option value="">${msg.localize("accounts.form.select")}</option>
            @for(AccountType type : AccountType.values())
                <optgroup label="${msg.lookup(type.titleKey())}">
                    @for(AccountDetailType detail : AccountDetailType.forType(type))
                        <option value="${detail.name()}">${msg.localize(detail.titleKey())}</option>
                    @endfor
                </optgroup>
            @endfor
        </select>
    </label>
    <%-- Валюта - фақат валютага боғланган турда кўринади (DEC-161 x-show);
         :disabled - боғланмаган турда select ЎЧИРИЛАДИ (disabled control submit
         қилинмайди - x-show яширин select stale currency'ни юбормайди); x-effect
         Alpine cur'ни тозалайди --%>
    <label x-show="window.accountCurrencyTypes.includes(dt)" x-cloak class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
        ${msg.localize("accounts.form.currency")}
        <select name="currency" x-model="cur" :disabled="!window.accountCurrencyTypes.includes(dt)"
                class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <option value="">${msg.localize("accounts.form.parentNone")}</option>
            @for(Currency c : currencies)
                <option value="${c.getCode()}">${c.getCode()} - ${c.getName()}</option>
            @endfor
        </select>
    </label>
    <div class="mt-1 flex items-center justify-between gap-3">
        <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" data-combo-close>${msg.localize("common.cancel")}</button>
        <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">${msg.localize("common.save")}</button>
    </div>
</form>
