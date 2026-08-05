@import com.averpo.erp.i18n.Msg
@import gg.jte.Content
@param Content items
@param Msg msg

<%-- Nargiza-007 P1a: рўйхат қаторидаги Action менюси (QBO ⋮ услуби);
     Penguin dropdown кўриниши - Arbitr-120 (реф: docs/penguin-refs/
     dropdown-menu.html). Panel position:fixed қилиб очилади - акс
     ҳолда жадвал ўрамининг overflow-x-auto'си кесиб қўярди;
     координата тугма жойидан олинади. data-rowmenu - T0 row-click
     панел ичидаги босишни четлаб ўтади (main.jte'даги глобал
     тингловчи шу белгига қарайди). Бандлар (items) услуби панел
     тегидаги [&_a]/[&_button] arbitrary variant'ларида - чақирувчи
     класс ёзмайди, фақат <a href>/<button> беради; button бандга
     border-0/bg-transparent ҳам берилган (preflight компенсацияси
     button:not([class])'га рамка беради - utilities устун келади). --%>
<div class="relative" data-rowmenu x-data="{open:false, top:0, right:0}">
    <button type="button" aria-label="${msg.lookup("common.actions")}" aria-haspopup="menu"
            x-bind:aria-expanded="open ? 'true' : 'false'"
            class="cursor-pointer rounded-radius px-2 py-1 text-base leading-none text-on-surface transition hover:bg-surface-alt hover:text-on-surface-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:bg-surface-dark-alt dark:hover:text-on-surface-dark-strong dark:focus-visible:outline-primary-dark"
            x-on:click="open = !open; var r = $el.getBoundingClientRect(); top = r.bottom + 4; right = Math.max(8, window.innerWidth - r.right);">⋮</button>
    <div x-show="open" x-on:click.outside="open = false" x-on:keydown.escape.window="open = false" x-cloak role="menu"
         x-bind:style="'position:fixed;top:' + top + 'px;right:' + right + 'px;'"
         class="z-30 flex w-fit min-w-52 flex-col overflow-hidden rounded-radius border border-outline bg-surface-alt shadow-lg dark:border-outline-dark dark:bg-surface-dark-alt [&_a]:block [&_a]:px-4 [&_a]:py-2 [&_a]:text-left [&_a]:text-sm [&_a]:text-on-surface [&_a]:no-underline [&_a:hover]:bg-surface-dark-alt/5 [&_a:hover]:text-on-surface-strong dark:[&_a]:text-on-surface-dark dark:[&_a:hover]:bg-surface-alt/5 dark:[&_a:hover]:text-on-surface-dark-strong [&_button]:block [&_button]:w-full [&_button]:cursor-pointer [&_button]:border-0 [&_button]:bg-transparent [&_button]:px-4 [&_button]:py-2 [&_button]:text-left [&_button]:text-sm [&_button]:text-on-surface [&_button:hover]:bg-surface-dark-alt/5 [&_button:hover]:text-on-surface-strong dark:[&_button]:text-on-surface-dark dark:[&_button:hover]:bg-surface-alt/5 dark:[&_button:hover]:text-on-surface-dark-strong">
        ${items}
    </div>
</div>
