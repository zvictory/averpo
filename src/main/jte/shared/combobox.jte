@import com.averpo.erp.i18n.Msg
@import gg.jte.Content
@param String name
@param Msg msg
@param Content options
@param String value = null
@param String placeholder = null
@param boolean required = false
@param boolean dense = false
@param String cls = ""
@param String xShow = null
@param boolean cloak = false
@param String onchange = null
@param String addUrl = null
@param String addLabel = null
@param String id = null
@param String hxGet = null
@param String hxTarget = null
@param boolean itemsel = false
@param String currencyTarget = null
@param String homeCurrency = null
@param String dataType = null
@param String dataCurrency = null
@param String dataBalance = null
@param String dataBal = null
@param String dataCur = null
@param String dataUnit = null

<%-- Penguin Combobox (DEC-123; реф: docs/penguin-refs/combobox.html
     «with search», мослашувлар реф изоҳида; мантиқ: static/js/
     penguin-combobox.js Alpine.data('pgCombo')). Қиймат value input'да
     (name айнан эски select name - POST шакли ўзгармас); танланган
     вариант data-* атрибутлари шу input'га кўчади ва change (bubbles)
     отилади. options - @template.shared.comboOption/comboGroup/
     comboAccountOptions чақириқлари. id/itemsel/currencyTarget/
     homeCurrency/data* параметрлари value input атрибутлари (JS ҳук/
     маркер ва бошланғич танлов маълумоти; JTE null атрибутни ташлаб
     юборади). onchange - value input'даги x-on:change ифодаси
     ($event.target = value input).
     addUrl/addLabel - рўйхат пастидаги доимий «+ Янги ...» банди
     (мавжуд quickForm оқими, механика combobox.md 066 билан айнан).
     Required учун value input 1px кўринар - validation bubble чиқади,
     invalid фокусда дропдаун очилади. --%>
<div x-data="pgCombo" x-id="['pgcombo']" data-combobox
     data-error-text="${msg.lookup("combo.error")}"
     data-placeholder="${placeholder}"
     class="relative ${cls}"
     x-show="${xShow}"
     x-cloak="${cloak}"
     x-on:keydown.esc="escKey($event)"
     x-on:keydown="typeAhead($event)"
     x-on:scroll.capture.window="reposition()"
     x-on:resize.window="reposition()">
    <%-- Компонент одатда <label> ичида: label ноинтерактив болага
         босилганда БИРИНЧИ labelable авлодга click юборади (preventDefault
         тўхтатмайди - Chrome), панел ичидаги ҳар босиш триггер тугмани
         қайта «босарди». Шу disabled кўринмас тугма биринчи labelable
         бўлиб қурбон вазифасини ўтайди - disabled'га activation йўқ. --%>
    <button type="button" disabled hidden tabindex="-1" aria-hidden="true"></button>
    <%-- Триггер тугма (Penguin canon): танланган ёрлиқ + шеврон --%>
    <button type="button" role="combobox" x-ref="btn"
            class="inline-flex w-full cursor-pointer items-center justify-between gap-2 rounded-radius border border-outline bg-surface-alt px-2 ${dense ? "py-1.5" : "py-2"} text-sm text-on-surface transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"
            aria-haspopup="listbox"
            x-bind:aria-controls="$id('pgcombo')"
            x-on:click="toggle()"
            x-on:keydown.down.prevent="openKb()"
            x-on:keydown.enter.prevent="openKb()"
            x-on:keydown.space.prevent="openKb()"
            x-bind:aria-expanded="open || openedWithKeyboard"
            x-bind:aria-label="label || ($root.dataset.placeholder || '')">
        <span class="truncate font-normal" x-bind:class="label ? '' : 'text-current/60'"
              x-text="label || ($root.dataset.placeholder || '')">${placeholder}</span>
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="size-5 shrink-0 text-current/60" aria-hidden="true">
            <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd"/>
        </svg>
    </button>
    <%-- Қиймат input (Penguin hiddenTextField): 1px кўринар - required
         validation bubble учун (display:none бўлса submit жим синарди).
         data-* параметрлар - бошланғич танлов маълумоти (кейин pgCombo
         танланган li'дан янгилайди) ва JS ҳук/маркерлар; hx-get берилса
         change'да hx-target'га partial юкланади (hx-include=this). --%>
    <input type="text" x-ref="value" name="${name}" value="${value}" id="${id}"
           required="${required}" autocomplete="off" tabindex="-1"
           class="pointer-events-none absolute bottom-0 left-2 size-px opacity-[.01]"
           data-itemsel="${itemsel}"
           data-currency-target="${currencyTarget}"
           data-home-currency="${homeCurrency}"
           data-type="${dataType}"
           data-currency="${dataCurrency}"
           data-balance="${dataBalance}"
           data-bal="${dataBal}"
           data-cur="${dataCur}"
           data-unit="${dataUnit}"
           hx-get="${hxGet}"
           hx-target="${hxTarget}"
           hx-trigger="${hxGet != null ? "change" : null}"
           hx-include="${hxGet != null ? "this" : null}"
           x-on:focus="fromValidation()"
           x-on:change="${onchange}">
    <%-- Панел: қидирув + рўйхат (fixed - .table-wrap/drawer клипидан
         қочади, жойлашув position() да) --%>
    <div x-cloak x-show="open || openedWithKeyboard"
         x-bind:id="$id('pgcombo')"
         x-bind:style="panelStyle"
         class="fixed z-[100] flex flex-col overflow-hidden rounded-radius border border-outline bg-surface-alt shadow-lg dark:border-outline-dark dark:bg-surface-dark-alt"
         x-on:click.outside="close()"
         x-on:keydown.down.prevent="$focus.wrap().next()"
         x-on:keydown.up.prevent="$focus.wrap().previous()"
         x-transition x-trap="openedWithKeyboard">
        <div class="relative shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" stroke="currentColor" fill="none" stroke-width="1.5" class="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-on-surface/50 dark:text-on-surface-dark/50" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z"/>
            </svg>
            <input type="text" x-ref="search" autocomplete="off" spellcheck="false"
                   class="w-full rounded-none border-b border-b-outline bg-surface-alt py-2 pl-8 pr-3 text-sm text-on-surface focus:outline-hidden focus-visible:border-primary dark:border-b-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark dark:focus-visible:border-primary-dark"
                   aria-label="${msg.lookup("combo.search")}"
                   placeholder="${msg.lookup("combo.search")}"
                   x-on:input="filter($el.value)"
                   x-on:keydown.enter.prevent="chooseFirst()"
                   x-on:change.stop>
        </div>
        <ul x-ref="list" role="listbox"
            class="flex min-h-0 grow flex-col overflow-y-auto py-1">
            <li x-ref="empty" class="hidden px-2 py-2 text-sm text-current/60">${msg.lookup("combo.empty")}</li>
            ${options}
        </ul>
        @if(addUrl != null)
            <%-- Доимий «+ Янги ...» банди - рўйхат пастида, филтрга
                 бўйсунмайди (QBO «+ Add new» қолипи) --%>
            <button type="button"
                    class="shrink-0 cursor-pointer border-t border-outline px-2 py-2 text-left text-sm font-semibold text-primary transition hover:bg-primary/10 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:text-primary-dark dark:hover:bg-primary-dark/10"
                    x-on:click="openAdd('${addUrl}')">${addLabel}</button>
        @endif
    </div>
    @if(addUrl != null)
        <%-- Quick-add модали (Penguin modal, реф: modal.html) - ичига
             мавжуд quickForm fragment'и юкланади; submit AJAX (pgCombo.
             addSubmit), Бекор - [data-combo-close] --%>
        <div x-cloak x-show="addOpen" x-trap.noscroll="addOpen"
             class="fixed inset-0 z-[110] flex items-center justify-center p-4"
             role="dialog" aria-modal="true" x-bind:aria-label="addTitle">
            <div class="absolute inset-0 bg-surface-dark/45" x-on:click="closeAdd()"></div>
            <div class="relative max-h-[85vh] w-full max-w-[420px] overflow-y-auto rounded-radius border border-outline bg-surface p-4 text-on-surface shadow-xl dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark">
                <div class="mb-3 flex items-center justify-between gap-3">
                    <b class="text-base text-on-surface-strong dark:text-on-surface-dark-strong" x-text="addTitle"></b>
                    <button type="button"
                            class="cursor-pointer rounded-radius px-2 py-0.5 text-xl leading-none text-current/60 transition hover:bg-surface-alt hover:text-on-surface-strong dark:hover:bg-surface-dark-alt dark:hover:text-on-surface-dark-strong"
                            x-on:click="closeAdd()" aria-label="${msg.lookup("common.close")}">✕</button>
                </div>
                <div x-ref="addBody"
                     x-on:submit="addSubmit($event)"
                     x-on:click="addClick($event)"></div>
            </div>
        </div>
    @endif
</div>
