@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.domain.Currency
@import com.averpo.erp.shared.web.Fmt
@import java.util.List
@param List<Currency> currencies
@param String selected
@param String exchangeRate
@param String homeCurrency
@param String dateInputName
@param String labelKey
@param String rateKey
@param Msg msg
@param String currencyName = "currency"
@param String currencyId = null
<%-- Параметрлар: currencies - валюта каталоги; selected - round-trip
     ҳужжат валютаси коди (null - home); exchangeRate - КАНОНИК курс
     (1 doc = rate home; null - "1"); dateInputName - prefill сана
     майдони (мас. "cmDate"); labelKey/rateKey - i18n; currencyName -
     select name (одатда "currency"); currencyId - 087 contact-currency.js
     мослиги учун (null - id'сиз).

     Курс блоки компоненти (DEC-097): валюта select + курс майдони.
     Форма id'ларига боғланмайди - rate-block.js data-атрибутлар билан
     form ичида scoped ишлайди (currencyId фақат 087 contact-currency.js
     мослиги учун - у ҳамон id билан топади).

     КРИТИК: сақланадиган exchangeRate КАНОНИК (1 doc = rate home) -
     hidden name="exchangeRate"'да, server/servis ТЕГИЛМАЙДИ. Кўринадиган
     input эса кучли-валюта базисида (Fmt.orient): флипда visible=1/canonical,
     ёрлиқ «1 home = ? doc». Биринчи кўриниш server ориентацияси билан
     JS'siz ҳам тўғри. --%>
!{var oi = Fmt.orientInput(selected, homeCurrency, exchangeRate);}
<label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
    ${msg.localize(labelKey)}
    <%-- Валюта - native select (DEC-123 сурув қарори: 3-10 ёзув,
         rate-block.js/contact-currency.js программ ёзади) --%>
    <select name="${currencyName}" id="${currencyId}" class="mt-1 block rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" data-rate-currency
           >
        @for(Currency currency : currencies)
            <option value="${currency.getCode()}"
                    selected="${selected != null ? currency.getCode().equals(selected) : currency.getCode().equals(homeCurrency)}">
                ${currency.getCode()}
            </option>
        @endfor
    </select>
</label>
<%-- Курс майдони фақат ҳужжат валютаси != home бўлганда кўринади (QBO
     қолипи). data-rate-flipped: визуал йўналиш каноникдан тескарими.
     DEC-134: лабел матни + валютажуфт спанлари + (?) БИТТА nowrap
     span ичида - flex-col label ҳар болакни алоҳида flex item қилиб
     вертикал устма-уст ташлаб юборарди («Rate (1 / USD / = ?» синиши);
     min-w - грид катаги курс блокидан тор бўлиб қолмасин. --%>
<label data-rate-block data-home="${homeCurrency}" data-date="${dateInputName}"
       data-rate-flipped="${oi.flipped() ? "1" : "0"}"
       class="flex min-w-[200px] flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark"
       style="${oi.foreign() ? null : "display:none"}">
    <span class="inline-flex items-center gap-1 whitespace-nowrap">${msg.localize(rateKey)} (1&nbsp;<span data-rate-base>${oi.base()}</span>&nbsp;= ?&nbsp;<span data-rate-quote>${oi.quote()}</span>) @template.shared.help(key = "field.exchangeRate", msg = msg)</span>
    <%-- Кўринадиган input: ориентацияли қиймат, name ЙЎҚ (submit қилмайди) --%>
    <input data-rate-input size="10" inputmode="decimal"
           value="${oi.visible()}"
           class="mt-1 block text-right rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
    <%-- Сақланадиган КАНОНИК курс: server/servis айнан шуни олади --%>
    <input type="hidden" name="exchangeRate" data-rate-canonical value="${oi.canonical()}">
</label>
