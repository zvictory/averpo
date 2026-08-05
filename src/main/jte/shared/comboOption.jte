@param String value
@param String label
@param boolean selected = false
@param String dataType = null
@param String dataCurrency = null
@param String dataBalance = null
@param String dataBal = null
@param String dataCur = null
@param String dataUndep = null
@param String dataUnit = null
@param String dataAcctype = null

<%-- Combobox варианти (DEC-123, shared/combobox.jte options ичида).
     Класслар Penguin combobox li канонидан (реф: docs/penguin-refs/
     combobox.html; px-4→px-2 зич мослашув, галочка Alpine binding
     ўрнига group-aria-selected - минг li'да арзон). data-* параметрлар
     каталогга хос маълумот (тур/валюта/баланс...): танловда pgCombo
     уларни value input'га кўчиради - эски selectedOptions[0].dataset
     ўқувчилар input.dataset ўқийди. --%>
<li class="group flex w-full cursor-pointer items-center justify-between gap-4 bg-surface-alt px-2 py-2 text-sm text-on-surface hover:bg-surface-dark-alt/5 hover:text-on-surface-strong focus-visible:bg-surface-dark-alt/5 focus-visible:text-on-surface-strong focus-visible:outline-hidden aria-selected:font-semibold dark:bg-surface-dark-alt dark:text-on-surface-dark dark:hover:bg-surface-alt/5 dark:hover:text-on-surface-dark-strong dark:focus-visible:bg-surface-alt/10 dark:focus-visible:text-on-surface-dark-strong"
    role="option" tabindex="0"
    data-value="${value}"
    aria-selected="${selected ? "true" : null}"
    data-type="${dataType}"
    data-currency="${dataCurrency}"
    data-balance="${dataBalance}"
    data-bal="${dataBal}"
    data-cur="${dataCur}"
    data-undep="${dataUndep}"
    data-unit="${dataUnit}"
    data-acctype="${dataAcctype}"
    x-on:click="choose($el)"
    x-on:keydown.enter="choose($el)">
    <span data-label class="truncate">${label}</span>
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" stroke="currentColor" fill="none" stroke-width="2" class="invisible size-4 shrink-0 group-aria-selected:visible" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5"/>
    </svg>
</li>
