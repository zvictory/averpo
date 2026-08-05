@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.security.domain.Permission
@import com.averpo.erp.web.Perms
@import com.averpo.erp.web.LayoutInfo
@import com.averpo.erp.web.Assets
@param Msg msg
@param Perms perms
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String lang = "uz"

<%-- Arbitr-117: сайдбар Penguin «Sidebar with collapsible menus» қолипида
     (спец: docs/modules/ui-framework.md, Sidebar бўлими; реф:
     docs/penguin-refs/). Уч зона: бренд (white-label, 112 мантиғи айнан),
     навигация гуруҳлари (x-collapse, $persist), user-profile меню.
     МУҲИМ: x-data ҳолати (nav/rail/dark/grps/toggleGrp/railDesk/grpOpen)
     main.jte'даги ташқи div'да - бу <nav> уни Alpine иерархияси орқали
     ўқийди (ЎЗ ўрами йўқ, Arbitr-111 қоидаси). Роль кўриниш шартлари
     (perms.view/edit) 092'дан АЙНАН кўчирилган - ўзгартирилмаган.

     Режимлар: desktop тўлиқ (lg:w-64 - СТАТИК рўйхатда, биринчи paint'да
     кенглик тайёр бўлсин деб; Arbitr-129) ↔ иконка-рейл (sb-rail маркер
     класси, in-[.sb-rail]:lg:* вариантлар орқали; группада hover flyout,
     якка пунктда tooltip) - рейл important'лари (lg:w-16! lg:p-2!) статик
     lg:w-64/p-3 дан ғолиб; мобил (<lg) drawer - overlay + x-trap + Escape.
     translate-x-0! (important) - статик -translate-x-60 билан бир хил
     хусусият тўқнашувида очиқ drawer ғолиб бўлиши учун. --%>
<nav id="sbnav"
     class="fixed left-0 top-0 z-50 flex h-svh w-60 shrink-0 -translate-x-60 flex-col border-r border-outline bg-surface-alt p-3 transition-[width,translate] duration-200 lg:sticky lg:z-30 lg:w-64 lg:translate-x-0 lg:self-start dark:border-outline-dark dark:bg-surface-dark-alt print:hidden"
     x-bind:class="[nav ? 'translate-x-0!' : '', rail ? 'sb-rail lg:w-16! lg:p-2!' : '']"
     x-trap.noscroll="nav"
     x-on:keydown.escape.window="nav = false"
     aria-label="${msg.lookup("common.menu")}">
    <%-- ======== ТЕПА: white-label бренд (3 поғона) - логотип созланса
         ўша; бўлмаса онбординг тугагач (showCompanyName) компания номи;
         ҳали тугамаган ҳолда AVERPO вендор fallback (фойдаланувчи
         2026-07-17: боши AVERPO, setup'дан кейин компания номи). Рейл
         тугмаси ёнида. lay БИР МАРТА шу ерда - sbfoot аватар ҳам ишлатади. --%>
    !{var lay = LayoutInfo.current();}
    <div class="mb-2 flex items-center justify-between gap-2 px-1 in-[.sb-rail]:lg:justify-center in-[.sb-rail]:lg:px-0">
        <div class="flex min-w-0 items-center in-[.sb-rail]:lg:hidden">
            <%-- Бренд зонаси (лого/ном/AVERPO поғонаси) босилса бош саҳифа
                 очилади - учала поғона бир хил ўралади (изчиллик).
                 no-underline text-inherit: h2/лого кўриниши айнан қолади
                 (ранг/underline ўзгармайди) - фақат клавиатура учун
                 focus-visible ринги (канон) қўшилади. --%>
            <a href="/" class="block min-w-0 rounded-radius text-inherit no-underline focus:outline-hidden focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:focus-visible:outline-primary-dark">
                @if(lay.hasBrandLogo())
                    <img src="/company/brand-logo" alt="${lay.companyName()}" class="max-h-7 max-w-[160px] object-contain">
                @elseif(lay.showCompanyName())
                    <h2 class="truncate text-lg font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${lay.companyName()}</h2>
                @else
                    <h2 class="truncate text-lg font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${LayoutInfo.FALLBACK_BRAND}</h2>
                @endif
            </a>
        </div>
        <%-- Рейл (иконка) режими тугмаси - фақат desktop; ҳолат $persist --%>
        <button type="button"
                class="hidden shrink-0 cursor-pointer rounded-radius p-1.5 text-on-surface hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary lg:flex dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:focus-visible:outline-primary-dark"
                x-on:click="rail = !rail"
                x-bind:aria-label="rail ? '${msg.lookup("common.expand")}' : '${msg.lookup("common.collapse")}'">
            @template.icons.chevronsLeft(cls = "size-5 transition-transform in-[.sb-rail]:lg:rotate-180")
        </button>
    </div>
    <%-- ======== Глобал қидирув ТРИГГЕРИ (Arbitr-117в): майдон эмас,
         Penguin услубидаги тугма-кўринишли триггер (лупа + «Қидирув...»
         + Ctrl+K ёрлиғи) - босилганда ёки Ctrl/Cmd+K'да марказдаги
         command-palette очилади (modal main.jte'да, pal ҳолати root
         x-data'да). JS'сиз fallback: оддий <a href="/search"> - тўлиқ
         саҳифа. Рейлда энди фақат лупа иконка (tooltip билан) - у ҳам
         очади (эски «рейлда яширин» хулқи ўрнига, карта 1-банд).
         Мобил drawer'да ҳам шу триггер; очишда drawer ёпилади (палитра
         nav ташқарисида - Аралашда контентга эргашиб ОЧ қолади). --%>
    <div class="mb-2">
        <a href="/search" role="button" aria-haspopup="dialog"
           x-on:click.prevent="pal = true; nav = false"
           class="group/tip relative flex w-full cursor-pointer items-center gap-2 rounded-radius border border-outline bg-surface px-2 py-1.5 text-sm text-on-surface/60 no-underline transition hover:border-outline-strong hover:text-on-surface focus:outline-hidden focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:bg-surface-dark/50 dark:text-on-surface-dark/60 dark:hover:border-outline-dark-strong dark:hover:text-on-surface-dark dark:focus-visible:outline-primary-dark in-[.sb-rail]:lg:justify-center in-[.sb-rail]:lg:border-transparent in-[.sb-rail]:lg:bg-transparent in-[.sb-rail]:lg:px-0 in-[.sb-rail]:lg:hover:bg-primary/5 dark:in-[.sb-rail]:lg:hover:bg-primary-dark/5">
            @template.icons.search(cls = "size-5 shrink-0")
            <span class="flex-1 truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("search.trigger")}</span>
            <kbd class="shrink-0 rounded-radius border border-outline px-1.5 py-0.5 font-sans text-[10px] font-medium text-on-surface/60 dark:border-outline-dark dark:text-on-surface-dark/60 in-[.sb-rail]:lg:hidden">Ctrl K</kbd>
            <span class="pointer-events-none absolute left-full top-1/2 z-40 ml-2 hidden -translate-y-1/2 whitespace-nowrap rounded-radius border border-outline bg-surface px-2 py-1 text-xs text-on-surface shadow-lg dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark in-[.sb-rail]:lg:group-hover/tip:block">${msg.localize("search.title")}</span>
        </a>
    </div>
    <%-- ======== «+ Янги» (Arbitr-119, фойдаланувчи 2026-07-17):
         қидирувдан ПАСТДА, «Бош саҳифа»дан ТЕПАДА - Penguin success
         (яшил) button кўринишида (buttons.html реф). Мазмун QBO «+ New»
         эталонида 3 устун (Мижозлар/Таъминотчилар/Бошқа) - навигация
         гуруҳлари эмас, ҳужжат яратиш ёрлиқлари. Payroll ва Счёт яратиш
         бу ерда КЎРСАТИЛМАЙДИ (QBO қолипи: payroll алоҳида, счёт Ҳисоблар
         режаси саҳифасида) - улар навигацияда қолади. Arbitr-092: тугма
         камида битта соҳада EDIT борларга, устун/ҳаволалар ўз соҳаси
         EDIT'и бўйича. Панел desktop'да сайдбар ёнидан флайаут,
         мобилда bottom-sheet; backdrop босилса/Esc'да ёпилади. Рейлда
         фақат + иконка (tooltip). id="createPanel" - ScreenSmokeTest
         маркери (рухсат филтрини текширади). Аралаш режимда панел
         КОНТЕНТ оиласи (ОЧ чиқади - фойдаланувчи 2026-07-17):
         wrapper'даги sb-light маркерини app.css @custom-variant
         истисноси sb-dark қамровидан чиқаради; тўлиқ Тўқда тўқлигича
         қолади. --%>
    @if(perms.anyEdit())
        <div class="relative mb-2" x-data="{plus: false}" x-on:keydown.escape.window="plus = false">
            <button type="button" x-on:click="plus = !plus" x-bind:aria-expanded="plus ? 'true' : 'false'"
                    class="group/tip relative flex w-full cursor-pointer items-center justify-center gap-1 whitespace-nowrap rounded-radius border border-success bg-success px-3 py-2 text-sm font-medium tracking-wide text-on-success transition hover:opacity-75 focus:outline-hidden focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-success active:opacity-100 active:outline-offset-0 in-[.sb-rail]:lg:px-0">
                @template.icons.plus(cls = "size-4 shrink-0")
                <span class="in-[.sb-rail]:lg:hidden">${msg.localize("plusNew.button")}</span>
                <span class="pointer-events-none absolute left-full top-1/2 z-40 ml-2 hidden -translate-y-1/2 whitespace-nowrap rounded-radius border border-outline bg-surface px-2 py-1 text-xs text-on-surface shadow-lg dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark in-[.sb-rail]:lg:group-hover/tip:block">${msg.localize("plusNew.button")}</span>
            </button>
            <div x-show="plus" x-cloak class="sb-light">
                <%-- ДИҚҚАТ: nav'да translate бор - transformed ота fixed
                     авлодлар учун боғлам блокига айланади (viewport эмас).
                     nav доим (0,0)да тургани учун left-0/top-0 + w-screen/
                     h-screen айнан viewport'ни қоплайди - inset-x-0/inset-0
                     эса nav энига сиқилиб қоларди. --%>
                <div class="fixed left-0 top-0 z-40 h-screen w-screen bg-surface-dark/25 backdrop-blur-xs" x-on:click="plus = false"></div>
                <div id="createPanel" role="menu" aria-label="${msg.lookup("plusNew.button")}"
                     class="fixed bottom-0 left-0 z-50 max-h-[85vh] w-screen overflow-y-auto rounded-t-2xl border border-outline bg-surface p-4 text-on-surface scrollbar-thin scrollbar-thumb-outline scrollbar-track-transparent lg:absolute lg:bottom-auto lg:left-full lg:top-0 lg:ml-2 lg:max-h-[80vh] lg:w-[min(720px,calc(100vw-20rem))] lg:rounded-radius lg:shadow-xl dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark dark:scrollbar-thumb-outline-dark">
                    <%-- Сарлавҳа+✕ фақат мобил bottom-sheet'да (десктопда устунлар аниқ) --%>
                    <div class="mb-3 flex items-center justify-between lg:hidden">
                        <b class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("plusNew.button")}</b>
                        <button type="button" x-on:click="plus = false" aria-label="${msg.lookup("common.close")}"
                                class="cursor-pointer rounded-radius p-1 text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5">
                            @template.icons.x(cls = "size-5")
                        </button>
                    </div>
                    <div class="grid grid-cols-1 gap-x-5 gap-y-5 lg:[grid-template-columns:repeat(auto-fit,minmax(160px,1fr))]">
                        <%-- Устун 1 - Мижозлар (QBO «Customers»): сотув ҳужжатлари +
                             мижоз, тартиб QBO «+ New» кетма-кетлигида (ҳисоб-фактура →
                             тўлов → смета → кредит-нота → чек → қайтариш → мижоз) --%>
                        @if(perms.edit(Permission.SALES))
                            <div>
                                <b class="mb-2 block border-b border-outline pb-1.5 text-sm font-semibold uppercase tracking-wider text-on-surface/80 dark:border-outline-dark dark:text-on-surface-dark/80">${msg.localize("nav.customers")}</b>
                                <a href="/invoices/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.invoice")}</a>
                                <a href="/invoice-payments/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.receipt")}</a>
                                <a href="/estimates/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.estimate")}</a>
                                <a href="/credit-memos/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.creditMemo")}</a>
                                <a href="/sales-receipts/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.salesReceipt")}</a>
                                <a href="/refund-receipts/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.refundReceipt")}</a>
                                <a href="/customers/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.customer")}</a>
                            </div>
                        @endif
                        <%-- Устун 2 - Таъминотчилар (QBO «Suppliers»): харид ҳужжатлари +
                             таъминотчи. Чиқим (Expense) URL харитасида PURCHASE
                             соҳасида - табиий жойи шу устун (гейт PURCHASE) --%>
                        @if(perms.edit(Permission.PURCHASE))
                            <div>
                                <b class="mb-2 block border-b border-outline pb-1.5 text-sm font-semibold uppercase tracking-wider text-on-surface/80 dark:border-outline-dark dark:text-on-surface-dark/80">${msg.localize("nav.vendors")}</b>
                                <a href="/expenses/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.expense")}</a>
                                <a href="/bills/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.bill")}</a>
                                <a href="/payments/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.payment")}</a>
                                <a href="/purchase-orders/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.purchaseOrder")}</a>
                                <a href="/vendor-credits/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.vendorCredit")}</a>
                                <a href="/vendors/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.vendor")}</a>
                            </div>
                        @endif
                        <%-- Устун 3 - Бошқа (QBO «Other»): банк/журнал/омбор ва товар.
                             Устун гейти учала соҳа OR - ичкарида ҳар ҳавола ЎЗ
                             соҳаси EDIT'и бўйича (масалан фақат GL роль шу устунда
                             Журнални кўради). Товар (Item) каталоги INVENTORY
                             соҳасида - шу устунда --%>
                        @if(perms.edit(Permission.BANKING) || perms.edit(Permission.GL) || perms.edit(Permission.INVENTORY))
                            <div>
                                <b class="mb-2 block border-b border-outline pb-1.5 text-sm font-semibold uppercase tracking-wider text-on-surface/80 dark:border-outline-dark dark:text-on-surface-dark/80">${msg.localize("plusNew.groupOther")}</b>
                                @if(perms.edit(Permission.BANKING))
                                    <a href="/bank-transactions/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.bankTxn")}</a>
                                    <a href="/transfers/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.accountTransfer")}</a>
                                @endif
                                @if(perms.edit(Permission.GL))
                                    <a href="/journal-entries/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.entry")}</a>
                                @endif
                                @if(perms.edit(Permission.INVENTORY))
                                    <a href="/inventory/adjustments/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.adjustment")}</a>
                                    <a href="/inventory/transfers/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.transfer")}</a>
                                    <a href="/items/new" class="block rounded-radius px-1.5 py-1.5 text-[.95rem] text-primary hover:bg-primary/5 hover:underline dark:text-primary-dark dark:hover:bg-primary-dark/5">${msg.localize("plusNew.item")}</a>
                                @endif
                            </div>
                        @endif
                    </div>
                </div>
            </div>
        </div>
    @endif
    <%-- ======== ЎРТА: навигация. Гуруҳлар Penguin collapsible (x-collapse):
         очиқлик grps массивида ($persist, sbGrps), жорий экран гуруҳи
         автоочиқ (шу файл охиридаги эрта FOUC скрипти data-active-group
         қўяди - Arbitr-129, x-init пуш қилади). Фаол пункт: скрипт фақат
         aria-current="page" қўяди - ажралиш aria-[current=page]:
         вариантлари билан. Очиқ гуруҳ сарлавҳаси ФОНСИЗ (фақат chevron +
         text-strong; Arbitr-129 Қўшимча 2) - bg-primary/10 фақат фаол
         пунктники, чалғитмасин. Рейл-desktop'да гуруҳ рўйхати hover
         flyout бўлади (grpOpen/railDesk main.jte'да). --%>
    <%-- Скролл темага мос (Arbitr-117в): ингичка + outline оҳанглари,
         track шаффоф - Tailwind native scrollbar-* utility'лари
         (стандарт scrollbar-width/color хусусиятлари, янги CSS класс
         йўқ). Drawer (мобил) ҳам шу контейнер. Arbitr-129:
         scrollbar-gutter:stable - scrollbar келиб-кетиши ички кенгликни
         силжитмасин (жой олдиндан банд; рейлда overflow visible бўлгани
         учун gutter умуман йўқ - таъсирсиз); id'дан пастдаги эрта скрипт
         скролл ўрнини сақлаш/тиклашда фойдаланади. --%>
    <div id="sbscroll" class="flex flex-1 flex-col gap-1 overflow-y-auto [scrollbar-gutter:stable] pb-4 scrollbar-thin scrollbar-thumb-outline scrollbar-track-transparent dark:scrollbar-thumb-outline-dark in-[.sb-rail]:lg:overflow-visible">
        <a href="/"
           class="group/tip relative flex items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:justify-center">
            @template.icons.home(cls = "size-5 shrink-0")
            <span class="in-[.sb-rail]:lg:hidden">${msg.localize("nav.home")}</span>
            <span class="pointer-events-none absolute left-full top-1/2 z-40 ml-2 hidden -translate-y-1/2 whitespace-nowrap rounded-radius border border-outline bg-surface px-2 py-1 text-xs text-on-surface shadow-lg dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark in-[.sb-rail]:lg:group-hover/tip:block">${msg.localize("nav.home")}</span>
        </a>
        <%-- Arbitr-092: гуруҳлар соҳа рухсати бўйича (user-roles.md) -
             кўра олмайдиган роль учун гуруҳ УМУМАН render бўлмайди.
             Ичкаридаги айрим ҳаволалар бошқа соҳага тегишли бўлса
             (масалан Товарлар - INVENTORY) алоҳида гейтланади. --%>
        @if(perms.view(Permission.SALES))
            <div class="relative flex flex-col" data-g="sales" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('sales')" aria-controls="sbg-sales"
                        x-bind:aria-expanded="grpOpen('sales', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('sales') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.tag(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.sales")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('sales') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-sales" x-cloak x-collapse x-show="grpOpen('sales', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.sales")}</li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/customers" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.customers")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/invoices" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.invoices")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/sales-receipts" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.salesReceipts")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/estimates" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.estimates")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/invoice-payments" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.receipts")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/credit-memos" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.creditMemos")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/refund-receipts" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.refundReceipts")}</a>
                    </li>
                    <%-- Товарлар каталоги INVENTORY соҳасида (матрицада
                         SALES_MANAGER'да NONE) - алоҳида гейт --%>
                    @if(perms.view(Permission.INVENTORY))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/items" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.items")}</a>
                        </li>
                    @endif
                </ul>
            </div>
        @endif
        @if(perms.view(Permission.PURCHASE))
            <div class="relative flex flex-col" data-g="purchases" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('purchases')" aria-controls="sbg-purchases"
                        x-bind:aria-expanded="grpOpen('purchases', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('purchases') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.shoppingCart(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.purchases")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('purchases') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-purchases" x-cloak x-collapse x-show="grpOpen('purchases', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.purchases")}</li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/vendors" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.vendors")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/bills" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.bills")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/purchase-orders" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.purchaseOrders")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/payments" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.payments")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/vendor-credits" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.vendorCredits")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/landed-costs" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.landedCosts")}</a>
                    </li>
                </ul>
            </div>
        @endif
        @if(perms.view(Permission.BANKING) || perms.view(Permission.PURCHASE))
            <div class="relative flex flex-col" data-g="bank" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('bank')" aria-controls="sbg-bank"
                        x-bind:aria-expanded="grpOpen('bank', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('bank') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.buildingBank(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.bank")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('bank') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-bank" x-cloak x-collapse x-show="grpOpen('bank', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.bank")}</li>
                    @if(perms.view(Permission.BANKING))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/bank-transactions" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.bankTransactions")}</a>
                        </li>
                    @endif
                    <%-- Чиқимлар (Expense) спец URL харитасида PURCHASE соҳасида --%>
                    @if(perms.view(Permission.PURCHASE))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/expenses" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.expenses")}</a>
                        </li>
                    @endif
                    @if(perms.view(Permission.BANKING))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/transfers" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.transfers")}</a>
                        </li>
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reconciliation" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.reconciliation")}</a>
                        </li>
                    @endif
                </ul>
            </div>
        @endif
        <%-- Payroll (Иш ҳақи) гуруҳи (payroll.md) --%>
        @if(perms.view(Permission.PAYROLL))
            <div class="relative flex flex-col" data-g="payroll" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('payroll')" aria-controls="sbg-payroll"
                        x-bind:aria-expanded="grpOpen('payroll', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('payroll') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.users(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.payroll")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('payroll') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-payroll" x-cloak x-collapse x-show="grpOpen('payroll', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.payroll")}</li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/employees" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.employees")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/payroll" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.payrollRuns")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/payroll/payments" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.payrollPayments")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/reports/payroll-register" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.payrollRegister")}</a>
                    </li>
                </ul>
            </div>
        @endif
        <%-- Arbitr-106: «Даврни ёпиш» сайдбарда ЭМАС - journalEntries тепа
             тугмаси ва /settings ичидаги ҳавола орқали (QBO қолипи). --%>
        @if(perms.view(Permission.GL))
            <div class="relative flex flex-col" data-g="accounting" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('accounting')" aria-controls="sbg-accounting"
                        x-bind:aria-expanded="grpOpen('accounting', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('accounting') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.book(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.accounting")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('accounting') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-accounting" x-cloak x-collapse x-show="grpOpen('accounting', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.accounting")}</li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/accounts" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.accounts")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/journal-entries" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.entries")}</a>
                    </li>
                </ul>
            </div>
        @endif
        @if(perms.view(Permission.INVENTORY))
            <div class="relative flex flex-col" data-g="inventory" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('inventory')" aria-controls="sbg-inventory"
                        x-bind:aria-expanded="grpOpen('inventory', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('inventory') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.packageBox(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.inventory")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('inventory') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-inventory" x-cloak x-collapse x-show="grpOpen('inventory', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.inventory")}</li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/inventory/balances" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.stockBalances")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/inventory/movements" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.stockMovements")}</a>
                    </li>
                    <%-- Акт РЎЙХАТЛАРИ (093): сайдбар формага эмас, рўйхатга
                         олиб боради (арбитр hotfix 2026-07-12) --%>
                    @if(perms.edit(Permission.INVENTORY))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/inventory/adjustments" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("plusNew.adjustment")}</a>
                        </li>
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/inventory/transfers" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("plusNew.transfer")}</a>
                        </li>
                    @endif
                </ul>
            </div>
        @endif
        @if(perms.view(Permission.FIN_REPORTS) || perms.view(Permission.INVENTORY)
                || perms.view(Permission.PURCHASE) || perms.view(Permission.SALES)
                || perms.view(Permission.PAYROLL))
            <div class="relative flex flex-col" data-g="reports" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('reports')" aria-controls="sbg-reports"
                        x-bind:aria-expanded="grpOpen('reports', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('reports') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.chartBar(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.reports")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('reports') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-reports" x-cloak x-collapse x-show="grpOpen('reports', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.reports")}</li>
                    <%-- QBO Favorites тартиби: аввал асосий молиявий ҳисоботлар.
                         Arbitr-092: умумий молиявийлар FIN_REPORTS, соҳа
                         ҳисоботлари ўз соҳаси орқали (user-roles.md) --%>
                    @if(perms.view(Permission.FIN_REPORTS))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/balance-sheet" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.balanceSheet")}</a>
                        </li>
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/profit-loss" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.profitLoss")}</a>
                        </li>
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/profit-and-loss-by-class" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.plByClass")}</a>
                        </li>
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/trial-balance" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.trialBalance")}</a>
                        </li>
                    @endif
                    @if(perms.view(Permission.INVENTORY))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/inventory-valuation" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.invValuation")}</a>
                        </li>
                    @endif
                    <%-- QBO эталони: ҳамма ҳисоботлар марказий Reports бўлимида
                         (Nargiza-002) --%>
                    @if(perms.view(Permission.PURCHASE))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/ap-aging" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.apAging")}</a>
                        </li>
                    @endif
                    @if(perms.view(Permission.SALES))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/ar-aging" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.arAging")}</a>
                        </li>
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/statement" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.arStatement")}</a>
                        </li>
                    @endif
                    @if(perms.view(Permission.PAYROLL))
                        <li class="px-1 py-0.5 first:mt-2">
                            <a href="/reports/payroll-register" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.payrollRegister")}</a>
                        </li>
                    @endif
                </ul>
            </div>
        @endif
        @if(isAdmin != null && isAdmin)
            <div class="relative flex flex-col" data-g="settings" x-data="{h: false}"
                 x-on:mouseenter="h = true" x-on:mouseleave="h = false"
                 x-on:focusin="h = true" x-on:focusout="h = false">
                <button type="button" x-on:click="toggleGrp('settings')" aria-controls="sbg-settings"
                        x-bind:aria-expanded="grpOpen('settings', h) ? 'true' : 'false'"
                        class="flex w-full cursor-pointer items-center gap-2 rounded-radius px-2 py-1.5 text-sm font-medium underline-offset-2 focus:outline-hidden focus-visible:underline in-[.sb-rail]:lg:justify-center"
                        x-bind:class="grps.includes('settings') && !rail ? 'text-on-surface-strong dark:text-on-surface-dark-strong' : 'text-on-surface hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong'">
                    @template.icons.adjustments(cls = "size-5 shrink-0")
                    <span class="mr-auto truncate text-left in-[.sb-rail]:lg:hidden">${msg.localize("nav.settingsGroup")}</span>
                    <span class="shrink-0 transition-transform in-[.sb-rail]:lg:hidden" x-bind:class="grps.includes('settings') ? 'rotate-180' : ''">@template.icons.chevronDown(cls = "size-4")</span>
                </button>
                <ul id="sbg-settings" x-cloak x-collapse x-show="grpOpen('settings', h)"
                    x-bind:class="railDesk() && h ? 'lg:absolute lg:left-full lg:top-0 lg:z-40 lg:ml-1 lg:w-56 lg:rounded-radius lg:border lg:border-outline lg:bg-surface lg:p-1.5 lg:shadow-lg lg:dark:border-outline-dark lg:dark:bg-surface-dark' : ''">
                    <li class="hidden px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-on-surface/70 dark:text-on-surface-dark/70" x-bind:class="railDesk() && h ? 'lg:block' : ''">${msg.localize("nav.settingsGroup")}</li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/settings" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.settings")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/users" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.users")}</a>
                    </li>
                    <li class="px-1 py-0.5 first:mt-2">
                        <a href="/audit-log" class="flex items-center gap-2 rounded-radius px-2 py-1.5 pl-9 text-sm text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline aria-[current=page]:bg-primary/10 aria-[current=page]:font-medium aria-[current=page]:text-on-surface-strong aria-[current=page]:hover:bg-primary/10 dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:aria-[current=page]:bg-primary-dark/10 dark:aria-[current=page]:text-on-surface-dark-strong dark:aria-[current=page]:hover:bg-primary-dark/10 in-[.sb-rail]:lg:pl-2">${msg.localize("nav.auditLog")}</a>
                    </li>
                </ul>
            </div>
        @endif
    </div>
    <%-- ======== ПАСТ: user-profile item (Penguin profile menu қолипи) -
         аватар-инициал + исм + роль; меню юқорига очилади (рейлда ўнгга).
         Меню: Профиль, Компания маълумотлари (фақат SUPER_ADMIN -
         /settings/company SETTINGS соҳасида), тил алмаштиргич, Чиқиш
         (POST /logout + CSRF). Роль ёрлиғи: LayoutInfo'да роль йўқ ва
         java тегилмайди - Perms.current() нақшидаги render-thread ўқиш:
         SecurityContext'даги ROLE_* authority → user.role.* i18n калити. --%>
    !{var sbAuth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();}
    !{String sbRoleKey = null;}
    @if(sbAuth != null)
        @for(var sbGranted : sbAuth.getAuthorities())
            @if(sbGranted.getAuthority().startsWith("ROLE_"))
                !{sbRoleKey = sbGranted.getAuthority().substring(5);}
            @endif
        @endfor
    @endif
    <div class="relative mt-auto border-t border-outline pt-2 dark:border-outline-dark" x-data="{menu: false}"
         x-on:keydown.escape.window="menu = false">
        <button type="button" aria-haspopup="true" x-on:click="menu = !menu" x-bind:aria-expanded="menu ? 'true' : 'false'"
                class="group/tip relative flex w-full cursor-pointer items-center gap-2 rounded-radius p-2 text-left text-on-surface hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:focus-visible:outline-primary-dark in-[.sb-rail]:lg:justify-center in-[.sb-rail]:lg:p-1.5"
                x-bind:class="menu ? 'bg-primary/10 dark:bg-primary-dark/10' : ''">
            @if(lay.hasAvatar())
                <img src="/profile/image" alt="${msg.localize("nav.profile")}" class="size-8 shrink-0 rounded-full object-cover">
            @else
                <span class="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/15 text-sm font-semibold text-primary dark:bg-primary-dark/25 dark:text-on-primary-dark">${lay.initial()}</span>
            @endif
            <span class="flex min-w-0 flex-col in-[.sb-rail]:lg:hidden">
                <span class="truncate text-sm font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${lay.displayName() == null ? msg.lookup("nav.profile") : lay.displayName()}</span>
                @if(sbRoleKey != null)
                    <span class="truncate text-xs">${msg.localize("user.role." + sbRoleKey)}</span>
                @endif
            </span>
            <span class="pointer-events-none absolute left-full top-1/2 z-40 ml-2 hidden -translate-y-1/2 whitespace-nowrap rounded-radius border border-outline bg-surface px-2 py-1 text-xs text-on-surface shadow-lg dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark in-[.sb-rail]:lg:group-hover/tip:block" x-show="!menu">${lay.displayName() == null ? msg.lookup("nav.profile") : lay.displayName()}</span>
        </button>
        <div x-cloak x-show="menu" x-transition x-trap="menu" role="menu"
             x-on:click.outside="menu = false"
             x-on:keydown.down.prevent="$focus.wrap().next()"
             x-on:keydown.up.prevent="$focus.wrap().previous()"
             class="absolute bottom-full left-0 z-40 mb-1 w-56 divide-y divide-outline rounded-radius border border-outline bg-surface shadow-lg dark:divide-outline-dark dark:border-outline-dark dark:bg-surface-dark in-[.sb-rail]:lg:bottom-0 in-[.sb-rail]:lg:left-full in-[.sb-rail]:lg:mb-0 in-[.sb-rail]:lg:ml-1">
            <div class="flex flex-col py-1.5">
                <a href="/profile" role="menuitem"
                   class="flex items-center gap-2 px-2 py-1.5 text-sm font-medium text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong">
                    @template.icons.userCircle(cls = "size-5 shrink-0")
                    <span>${msg.localize("nav.profile")}</span>
                </a>
                @if(isAdmin != null && isAdmin)
                    <a href="/settings/company" role="menuitem"
                       class="flex items-center gap-2 px-2 py-1.5 text-sm font-medium text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong">
                        @template.icons.building(cls = "size-5 shrink-0")
                        <span>${msg.localize("company.title")}</span>
                    </a>
                @endif
            </div>
            <%-- Тил алмаштиргич сақланади (карта: user-profile меню ичи) --%>
            <div class="flex items-center gap-1 px-2 py-1.5" role="none">
                <a href="?lang=uz" class="${lang.equals("uz") ? "rounded-radius px-2 py-1 text-xs font-semibold text-primary dark:text-primary-dark" : "rounded-radius px-2 py-1 text-xs text-on-surface/70 hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark/70 dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong"}">ЎЗ</a>
                <a href="?lang=ru" class="${lang.equals("ru") ? "rounded-radius px-2 py-1 text-xs font-semibold text-primary dark:text-primary-dark" : "rounded-radius px-2 py-1 text-xs text-on-surface/70 hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark/70 dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong"}">РУ</a>
                <a href="?lang=en" class="${lang.equals("en") ? "rounded-radius px-2 py-1 text-xs font-semibold text-primary dark:text-primary-dark" : "rounded-radius px-2 py-1 text-xs text-on-surface/70 hover:bg-primary/5 hover:text-on-surface-strong dark:text-on-surface-dark/70 dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong"}">EN</a>
            </div>
            <%-- Кўриниш режими (Arbitr-117в): 3 ҳолатли танлов - Оч /
                 Тўқ / Аралаш (фақат sidebar тўқ). Penguin segmented
                 услуби (radiogroup); mode ҳолати root x-data'да,
                 $persist 'uiMode' (эски 'darkMode'дан FOUC скрипти
                 кўчиради), applyMode <html> class'ларини қўяди. --%>
            <div class="flex flex-col gap-1.5 px-2 py-2">
                <span class="text-xs text-on-surface/70 dark:text-on-surface-dark/70">${msg.localize("common.darkToggle")}</span>
                <div class="flex overflow-hidden rounded-radius border border-outline dark:border-outline-dark" role="radiogroup" aria-label="${msg.lookup("common.darkToggle")}">
                    <button type="button" role="radio" x-on:click="mode = 'light'"
                            x-bind:aria-checked="mode === 'light' ? 'true' : 'false'"
                            class="flex-1 cursor-pointer px-1 py-1.5 text-center text-xs font-medium focus:outline-hidden focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:focus-visible:outline-primary-dark"
                            x-bind:class="mode === 'light' ? 'bg-primary/10 font-semibold text-primary dark:bg-primary-dark/20 dark:text-primary-dark' : 'text-on-surface hover:bg-primary/5 dark:text-on-surface-dark dark:hover:bg-primary-dark/5'">
                        ${msg.localize("common.theme.light")}
                    </button>
                    <button type="button" role="radio" x-on:click="mode = 'dark'"
                            x-bind:aria-checked="mode === 'dark' ? 'true' : 'false'"
                            class="flex-1 cursor-pointer border-l border-outline px-1 py-1.5 text-center text-xs font-medium focus:outline-hidden focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:focus-visible:outline-primary-dark"
                            x-bind:class="mode === 'dark' ? 'bg-primary/10 font-semibold text-primary dark:bg-primary-dark/20 dark:text-primary-dark' : 'text-on-surface hover:bg-primary/5 dark:text-on-surface-dark dark:hover:bg-primary-dark/5'">
                        ${msg.localize("common.theme.dark")}
                    </button>
                    <button type="button" role="radio" x-on:click="mode = 'mix'"
                            x-bind:aria-checked="mode === 'mix' ? 'true' : 'false'"
                            class="flex-1 cursor-pointer border-l border-outline px-1 py-1.5 text-center text-xs font-medium focus:outline-hidden focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:focus-visible:outline-primary-dark"
                            x-bind:class="mode === 'mix' ? 'bg-primary/10 font-semibold text-primary dark:bg-primary-dark/20 dark:text-primary-dark' : 'text-on-surface hover:bg-primary/5 dark:text-on-surface-dark dark:hover:bg-primary-dark/5'">
                        ${msg.localize("common.theme.mix")}
                    </button>
                </div>
            </div>
            <div class="flex flex-col py-1.5">
                <form method="post" action="/logout" role="none">
                    @template.shared.csrf(csrf = csrf)
                    <button type="submit" role="menuitem"
                            class="flex w-full cursor-pointer items-center gap-2 px-2 py-1.5 text-left text-sm font-medium text-on-surface underline-offset-2 hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:underline dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong">
                        @template.icons.logout(cls = "size-5 shrink-0")
                        <span>${msg.localize("nav.logout")}</span>
                    </button>
                </form>
            </div>
        </div>
    </div>
    <%-- Arbitr-143: вендор логоси (powered-by туйғуси) - user profile'дан
         паст, nav охири. Икки <img> алмашуви (light `dark:hidden`, dark
         `hidden dark:block`) - тепа бренд (мижоз) билан аралашмайди, бу
         ВЕНДОР. Рейлда жой йўқ - яширин. Аралаш (sb-light) footer оч оила:
         light версия кўринади (sb-light маркери .dark қамровидан ташқари,
         app.css @custom-variant). ?v= кэш-бузиш (Arbitr-137).
         Arbitr-146: логотип averpo.com'га ҳавола (target=_blank + rel
         noopener noreferrer - хавфсизлик шарти); opacity-60 <a>да, hover/
         focus'да тўлиқ (босилиши мумкинлигини билдиради). --%>
    <div class="mt-3 border-t border-outline pt-3 in-[.sb-rail]:lg:hidden dark:border-outline-dark">
        <a href="https://averpo.com" target="_blank" rel="noopener noreferrer" aria-label="Averpo сайти"
           class="block rounded-radius opacity-60 transition hover:opacity-100 focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:focus-visible:outline-primary-dark">
            <img src="/img/averpo-logo-light.svg?v=${Assets.version()}" alt="Averpo" class="mx-auto block h-7 w-auto max-w-[120px] dark:hidden">
            <img src="/img/averpo-logo-dark.svg?v=${Assets.version()}" alt="Averpo" class="mx-auto hidden h-7 w-auto max-w-[120px] dark:block">
        </a>
    </div>
</nav>
<%-- Arbitr-129: sidebar FOUC ҳимояси - head'даги uiMode скрипти услубида,
     parser-blocking жойда (nav ёпилган заҳоти). Alpine defer билан кеч
     boot бўлгунча биринчи paint DEFAULT кўринишда қоларди (гуруҳлар
     ёпиқ, рейл ўрнига тўлиқ кенглик, скролл тепада) - бу скрипт
     localStorage ҳолатини Alpine boot'да ҳисобланадиган АЙНАН кўринишга
     синхрон келтиради: boot ҳеч нарсани ўзгартирмайди, x-collapse жим
     қолади, анимация фақат фойдаланувчи амалида. Persist калитлари ва
     toggleGrp/grpOpen мантиғи ўзгармаган (карта 4-банд).
     Фаол пункт (aria-current) ҳам шу ерда: data-active-group автоочишни
     биринчи paint'да билиш шарт, мантиқ икки нусха бўлмасин деб бутунлай
     шу скриптга жойлашган - main.jte x-init ўша атрибутни ўқийверади.
     МУҲИМ (Alpine setClasses undo бухгалтерияси): олдиндан қўйилган
     класслар alpine:init'да ечилади - Alpine ЎША синхрон таскда биндингдан
     қайта қўяди (орада paint йўқ - кўзга кўринмайди). Ечилмаса Alpine
     уларни «ўзи қўшмаган» деб кейинги toggle'да олиб ташлолмасди
     (рейл/гуруҳ ажралиши ёпишиб қоларди). --%>
<script>
    (function () {
        var nav = document.getElementById('sbnav');
        var sc = document.getElementById('sbscroll');
        if (!nav || !sc) return;

        // Фаол пункт: энг узун мос href ютади - фақат aria-current
        // қўйилади (ажралиш aria-[current=page]: вариантларида), гуруҳи
        // data-active-group бўлиб <html>'га ёзилади (қуйида автоочиш ва
        // main.jte x-init ўқийди)
        var path = location.pathname, best = null, bestLen = -1, ag = null;
        nav.querySelectorAll('a[href^="/"]').forEach(function (a) {
            var href = a.getAttribute('href');
            var hit = href === '/' ? path === '/' : (path === href || path.indexOf(href + '/') === 0);
            if (hit && href.length > bestLen) { best = a; bestLen = href.length; }
        });
        if (best) {
            best.setAttribute('aria-current', 'page');
            var g = best.closest('[data-g]');
            if (g) { ag = g.getAttribute('data-g'); document.documentElement.setAttribute('data-active-group', ag); }
        }

        // Persist қийматлари (Alpine Persist JSON формати) - парс хатоси
        // ёки йўқ калитда жим default (FOUC скрипт услуби)
        var rail = false, grps = [];
        try { rail = JSON.parse(localStorage.getItem('sbRail')) === true; } catch (e) {}
        try {
            var arr = JSON.parse(localStorage.getItem('sbGrps'));
            if (Array.isArray(arr)) grps = arr;
        } catch (e) {}
        // x-init автоочиш хулқи айнан: жорий гуруҳ рўйхатда бўлмаса очиқ
        // ҳисобланади (persist'га ёзишни аввалгидек x-init ўзи қилади)
        if (ag && grps.indexOf(ag) < 0) grps.push(ag);

        // Олдиндан қўйилган класслар рўйхати - alpine:init'да ечилади
        var pre = [];
        function put(el, classes) {
            var added = classes.filter(function (c) { return !el.classList.contains(c); });
            if (added.length) {
                added.forEach(function (c) { el.classList.add(c); });
                pre.push([el, added]);
            }
        }

        // Рейл: Alpine x-bind айнан шу классларни қўяди (important'лар
        // статик lg:w-64/p-3 дан ғолиб)
        if (rail) put(nav, ['sb-rail', 'lg:w-16!', 'lg:p-2!']);

        // Очиқ гуруҳлар - grpOpen натижаси айнан: рейл-desktop'да flyout
        // режими (рўйхатлар ёпиқ қолади), акс ҳолда grps бўйича. Очиқ
        // ul'дан x-cloak ечилади - display Alpine x-show=true ҳолати
        // билан бир хил бўлади (inline style ёзилмайди). Chevron ва
        // сарлавҳа ажралиши (chevron + text-strong, фонсиз - Қўшимча 2)
        // ҳам Alpine қўядиган айнан класслар - акс ҳолда chevron boot'да
        // transition-transform билан кўзга кўринарли айланиб қоларди.
        var railDesk = rail && window.matchMedia('(min-width: 64rem)').matches;
        nav.querySelectorAll('[data-g]').forEach(function (grp) {
            if (grps.indexOf(grp.getAttribute('data-g')) < 0) return;
            var ul = document.getElementById('sbg-' + grp.getAttribute('data-g'));
            if (ul && !railDesk) ul.removeAttribute('x-cloak');
            var chev = grp.querySelector('.transition-transform');
            if (chev) put(chev, ['rotate-180']);
            if (!rail) {
                var btn = grp.querySelector('button');
                if (btn) put(btn, ['text-on-surface-strong', 'dark:text-on-surface-dark-strong']);
            }
        });

        document.addEventListener('alpine:init', function () {
            pre.forEach(function (p) {
                p[1].forEach(function (c) { p[0].classList.remove(c); });
            });
        });

        // Скролл ўрни (sessionStorage): гуруҳлар очилгандан КЕЙИН
        // тикланади - баландлик шу пайтда тайёр. Ёзиш: скролл тинганда
        // (дебаунс) ва саҳифадан чиқишда (pagehide - линк босиб ўтишни
        // ҳам қамрайди)
        try {
            var sp = parseInt(sessionStorage.getItem('sbScroll'), 10);
            if (sp > 0) sc.scrollTop = sp;
        } catch (e) {}
        var t = null;
        function save() {
            try { sessionStorage.setItem('sbScroll', String(sc.scrollTop)); } catch (e) {}
        }
        sc.addEventListener('scroll', function () { clearTimeout(t); t = setTimeout(save, 120); }, {passive: true});
        window.addEventListener('pagehide', save);
    })();
</script>
