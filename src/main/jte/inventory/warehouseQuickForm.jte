@import com.averpo.erp.i18n.Msg
@param Msg msg
@param org.springframework.security.web.csrf.CsrfToken csrf = null

<%-- Combobox quick-add модал fragment'и (spec: combobox.md):
     ном + ихтиёрий код (омбор каталог формасидаги минимал тўплам).
     pgCombo (penguin-combobox.js) submit'ни ушлаб JSON {id,label} кутади;
     BR хато [data-combo-error]'да. --%>
<form method="post" action="/warehouses/quick" data-combo-quick
      data-combo-title="${msg.lookup("combo.title.warehouse")}"
      style="display:grid;gap:.9rem;">
    @template.shared.csrf(csrf = csrf)
    <p class="m-0 rounded-radius border border-danger/30 bg-danger/10 px-3 py-2.5 text-danger" data-combo-error hidden></p>
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
        ${msg.localize("warehouse.name")}
        <input name="name" required
               class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    </label>
    <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
        ${msg.localize("warehouse.code")}
        <input name="code"
               class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    </label>
    <div class="mt-1 flex items-center justify-between gap-3">
        <button type="button" class="cursor-pointer whitespace-nowrap rounded-radius border border-secondary bg-secondary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-secondary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-secondary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-secondary-dark dark:bg-secondary-dark dark:text-on-secondary-dark dark:focus-visible:outline-secondary-dark" data-combo-close>${msg.localize("common.cancel")}</button>
        <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-success bg-success px-4 py-2 text-center text-sm font-semibold tracking-wide text-on-success transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">${msg.localize("common.save")}</button>
    </div>
</form>
