@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.domain.CompanySettings
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.shared.domain.InventoryValuationMethod
@import java.util.List
@param CompanySettings settings
@param List<Currency> currencies
@param List<String> timezones
@param boolean currencyLocked
@param boolean valuationLocked = false
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param boolean setup = false
@param String error = null
@param String message = null
<%-- DEC-104: build диагностика маълумоти (controller BuildProperties'дан
     тайёрлайди - тест/build-info'сиз контекстда null, қатор чиқмайди) --%>
@param String buildVersion = null
@param String buildTime = null
@param String buildCommit = null

@template.layout.main(helpKey = "page.settings", title = msg.lookup("settings.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-4 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("settings.heading")}</h1>

    <%-- Онбординг banner (DEC-056): setup=1 да - янги ўрнатишда ADMIN
         бу ерга login'дан келади, аввал асосий созламаларни тўлдиради --%>
    @if(setup)
        <div class="mb-4 rounded-radius border border-info/40 bg-info/10 px-4 py-3.5 text-on-surface dark:text-on-surface-dark">
            ${msg.localize("settings.setupBanner")}
            <%-- Бошланғич маълумотни бир қадамда киритиш йўли (import-excel.md,
                 setup оқими билан бирлашади) --%>
            <div style="margin-top:.5rem;">
                <a class="font-semibold text-primary underline dark:text-primary-dark" href="/settings/import">${msg.localize("import.heading")}</a>
            </div>
        </div>
    @endif

    @template.shared.alert(message = message, error = error)

    <form method="post" action="/settings"
          class="grid max-w-[480px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("settings.companyName")}
            <input name="name" value="${settings.getName()}" required
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <%-- DEC-112: компаниянинг тўлиқ реквизитлари (юридик ном, банк,
             директор, лого) алоҳида саҳифада - чоп сарлавҳаси шундан ўқийди --%>
        <p style="font-size:.85rem;margin-top:-.5rem;">
            <a href="/settings/company" class="text-primary underline dark:text-primary-dark">${msg.localize("company.title")} →</a>
        </p>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            <span class="inline-flex items-center gap-1">${msg.localize("settings.homeCurrency")} @template.shared.help(key = "field.homeCurrency", msg = msg)</span>
            <select name="homeCurrency" disabled="${currencyLocked}"
                    class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                @for(Currency c : currencies)
                    <option value="${c.getCode()}" selected="${c.getCode().equals(settings.homeCurrencyCode())}">
                        ${c.getCode()} - ${c.getName()}
                    </option>
                @endfor
            </select>
        </label>
        @if(currencyLocked)
            <input type="hidden" name="homeCurrency" value="${settings.homeCurrencyCode()}">
            <p class="text-sm text-current/60">${msg.localize("settings.currencyLocked")}</p>
        @endif

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            <span class="inline-flex items-center gap-1">${msg.localize("settings.timezone")} @template.shared.help(key = "field.timezone", msg = msg)</span>
            <select name="timezone" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                @for(String tz : timezones)
                    <option value="${tz}" selected="${tz.equals(settings.getTimezone())}">${tz}</option>
                @endfor
            </select>
        </label>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            <span class="inline-flex items-center gap-1">${msg.localize("settings.valuation")} @template.shared.help(key = "field.valuationMethod", msg = msg)</span>
            <select name="inventoryValuation" disabled="${valuationLocked}"
                    class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                @for(InventoryValuationMethod m : InventoryValuationMethod.values())
                    <option value="${m.name()}" selected="${m == settings.getInventoryValuation()}">
                        ${msg.lookup(m.titleKey())}
                    </option>
                @endfor
            </select>
        </label>
        @if(valuationLocked)
            <input type="hidden" name="inventoryValuation" value="${settings.getInventoryValuation().name()}">
            <p class="text-sm text-current/60">${msg.localize("settings.valuationLocked")}</p>
        @endif

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            <span class="inline-flex items-center gap-1">${msg.localize("settings.fiscalYearStart")} @template.shared.help(key = "field.fiscalYearStart", msg = msg)</span>
            <select name="fiscalYearStartMonth" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                @for(int m = 1; m <= 12; m++)
                    <option value="${m}" selected="${m == settings.getFiscalYearStartMonth()}">
                        ${msg.localize("month." + m)}
                    </option>
                @endfor
            </select>
        </label>
        <p class="-mt-2 text-[.85rem] text-current/60">${msg.localize("settings.fiscalYearStartHint")}</p>

        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            <span class="inline-flex items-center gap-1">${msg.localize("settings.closingDate")} @template.shared.help(key = "field.closingDate", msg = msg)</span>
            <input type="date" name="closingDate"
                   value="${settings.getClosingDate() == null ? null : settings.getClosingDate().toString()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <p class="-mt-2 text-[.85rem] text-current/60">${msg.localize("settings.closingDateHint")}</p>
        <%-- DEC-106: даврни алоҳида саҳифада бошқариш ҳаволаси (QBO
             Settings→Advanced қолипи). journalEntries тепасидаги тугма ҳам
             айнан шу саҳифага олади - PERIOD_CLOSE-only CHIEF /settings'га
             кира олмагани учун унинг ягона йўли ўша тугма. --%>
        <p style="font-size:.85rem;margin-top:-.5rem;">
            <a href="/settings/closing-date" class="text-primary underline dark:text-primary-dark">${msg.localize("nav.closingDate")} →</a>
        </p>

        <%-- Payroll ставкалари (payroll.md, BR-SET-005: 0..100 фоиз, ADMIN) --%>
        <b style="margin-top:.5rem;">${msg.localize("settings.payrollRates")} @template.shared.help(key = "field.payrollRates", msg = msg)</b>
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:.9rem;align-items:end;">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("settings.incomeTaxRate")}
                <input name="incomeTaxRate" type="number" min="0" max="100" step="0.0001" required
                       value="${settings.getIncomeTaxRate().stripTrailingZeros().toPlainString()}"
                       class="mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("settings.pensionRate")}
                <input name="pensionRate" type="number" min="0" max="100" step="0.0001" required
                       value="${settings.getPensionRate().stripTrailingZeros().toPlainString()}"
                       class="mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("settings.socialTaxRate")}
                <input name="socialTaxRate" type="number" min="0" max="100" step="0.0001" required
                       value="${settings.getSocialTaxRate().stripTrailingZeros().toPlainString()}"
                       class="mt-1 w-full text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>
        <p class="-mt-2 text-[.85rem] text-current/60">${msg.localize("settings.payrollRatesHint")}</p>

        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
    </form>

    <%-- Бошқа каталог созламалари - ҳар модул ўз саҳифасида (қатлам йўналиши сақланади) --%>
    <h2 class="mb-3 mt-6 text-xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("settings.catalogs")}</h2>
    <div class="grid max-w-[480px] gap-2 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
        <a href="/settings/units" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.title")}</a>
        <a href="/settings/price-lists" class="text-primary underline dark:text-primary-dark">${msg.localize("plist.title")}</a>
        <a href="/settings/tax-rates" class="text-primary underline dark:text-primary-dark">${msg.localize("tax.title")}</a>
        <a href="/settings/payment-methods" class="text-primary underline dark:text-primary-dark">${msg.localize("pm.title")}</a>
        <a href="/settings/classes" class="text-primary underline dark:text-primary-dark">${msg.localize("cls.title")}</a>
        <a href="/settings/currencies" class="text-primary underline dark:text-primary-dark">${msg.localize("currency.title")}</a>
        <a href="/settings/warehouses" class="text-primary underline dark:text-primary-dark">${msg.localize("warehouse.title")}</a>
        <a href="/settings/import" class="text-primary underline dark:text-primary-dark">${msg.localize("import.heading")}</a>
        <%-- Плагинлар реестри (DEC-113): ихтиёрий фичаларни SUPER_ADMIN
             шу ердан ёқади/ўчиради - спец «Асосий Созламадан ҳавола» талаби --%>
        <a href="/settings/plugins" class="text-primary underline dark:text-primary-dark">${msg.localize("plugin.title")} →</a>
    </div>

    <%-- Хавфли ҳудуд (factory-reset.md): бутун тизимни бошланғич ҳолатга
         қайтариш. Фақат ADMIN - /settings ADMIN-only бўлса ҳам @if(isAdmin)
         гаров сифатида (danger амал, қизил услуб) --%>
    @if(isAdmin)
        <h2 class="mb-3 mt-7 text-xl font-bold text-danger">${msg.localize("reset.dangerZone")}</h2>
        <div class="max-w-[480px] rounded-radius border-2 border-danger/40 bg-danger/10 px-5 py-4">
            <p class="mb-3 text-[.92rem] text-danger">${msg.localize("reset.dangerZoneDesc")}</p>
            <a href="/settings/reset"
               class="inline-block rounded-radius bg-danger px-4 py-2 font-semibold text-on-danger no-underline">
                ${msg.localize("reset.button")}
            </a>
        </div>
    @endif

    <%-- DEC-104: build диагностика қатори (энг тагда, хира) - жонлида
         қайси версия/код турганини бир қарашда билиш. Вақт компания
         timezone'ида (Fmt.dt, темир қоида №12). Фақат /settings
         (SUPER_ADMIN) - маълумот ошкорлиги гигиенаси, login'га чиқмайди.
         buildVersion null (тест/build-info'сиз) - қатор умуман чиқмайди. --%>
    @if(buildVersion != null)
        <p class="mb-1 mt-8 text-[.8rem] text-current/50">
            AVERPO v${buildVersion} · build ${buildTime} · ${buildCommit}
        </p>
    @endif
`)
