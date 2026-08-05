@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.security.domain.Permission
@import com.averpo.erp.web.Assets
@import com.averpo.erp.web.Perms
@import com.averpo.erp.web.LayoutInfo
@import gg.jte.Content
@param String title
@param Content content
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
<%-- Arbitr-079: контекстли ёрдам - ихтиёрий саҳифа калити (help.<калит>.*
     i18n жуфтига ишора). null (default) - саҳифада (?) чиқмайди, мавжуд
     чақирувчилар ўзгармайди; опт-ин экранлар "page.x" беради. --%>
@param String helpKey = null
<%-- Arbitr-092: сайдбар/панел филтри учун соҳа рухсатлари. Параметр
     ЭМАС, статик ҳелпер: layout'га параметр қўшиш 102 саҳифа шаблонини
     ўзгартиришни талаб қиларди; render request thread'ида бўлгани учун
     SecurityContext'дан тўғридан-тўғри олинади. Server ҳақиқати
     SecurityConfig'да - бу фақат кўриниш. --%>
!{var perms = Perms.current();}

<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - AVERPO</title>
    <link rel="icon" type="image/svg+xml" href="/favicon.svg">
    <%-- Arbitr-131: асосий шрифт вазнлари preload - CSS парсини кутмай
         йўлга чиқади, биринчи ташрифда ҳам FOUT ойнаси минимал бўлади.
         crossorigin шрифт preload'ида same-origin'да ҳам МАЖБУРИЙ
         (font fetch'и CORS режимида - белгисиз браузер иккита алоҳида
         юклаш қилади). --%>
    <link rel="preload" href="/fonts/inter-cyrillic-400-normal.woff2" as="font" type="font/woff2" crossorigin>
    <link rel="preload" href="/fonts/inter-cyrillic-600-normal.woff2" as="font" type="font/woff2" crossorigin>
    <link rel="preload" href="/fonts/inter-latin-400-normal.woff2" as="font" type="font/woff2" crossorigin>
    <%-- Arbitr-117в: кўриниш режими биринчи paint'дан ОЛДИН қўлланади
         (FOUC ҳимояси) - Alpine defer, кеч юкланади; калит Alpine Persist
         билан умумий ('uiMode', JSON сатр: "light"/"dark"/"mix").
         Миграция: эски boolean 'darkMode' калити бир марта шу ерда
         кўчирилади (true→dark, бошқаси→light) - фойдаланувчи танлови
         йўқолмайди. 'mix' (Аралаш) - фақат sidebar тўқ: <html>'га
         sb-dark белгиси, қамрови app.css @custom-variant dark'да. --%>
    <script>
        (function () {
            var m = null;
            try { m = JSON.parse(localStorage.getItem('uiMode')); } catch (e) {}
            if (m !== 'light' && m !== 'dark' && m !== 'mix') {
                m = localStorage.getItem('darkMode') === 'true' ? 'dark' : 'light';
                try { localStorage.setItem('uiMode', JSON.stringify(m)); } catch (e) {}
            }
            if (m === 'dark') document.documentElement.classList.add('dark');
            if (m === 'mix') document.documentElement.classList.add('sb-dark');
        })();
    </script>
    <%-- Arbitr-137: ҳар локал css/js линкида ?v=<build вақти> - узоқ
         кэшдаги (30 кун) эски нусха deploy'дан кейин ишлатилмасин.
         Шрифт preload'лари бустланмайди - шрифт файллари ўзгармайди. --%>
    <link rel="stylesheet" href="/css/app.css?v=${Assets.version()}">
    <%-- Локал asset'лар - ERP ички тармоқ/офлайнда ҳам ишлайди --%>
    <script src="/vendor/htmx.min.js?v=${Assets.version()}"></script>
    <%-- Alpine 3 + расмий плагинлар (Arbitr-116): ҳаммаси defer, плагин
         файллари Alpine ядросидан ОЛДИН туради - Alpine ҳужжати шуни
         талаб қилади (плагин alpine:init'га ядро юкланишидан аввал уланади) --%>
    <script defer src="/js/vendor/alpine-mask.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine-collapse.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine-focus.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine-persist.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine.min.js?v=${Assets.version()}"></script>
    <style>
        /* Arbitr-122'дан кейинги ЯГОНА техник қолдиқ: экран услублари
           тўлиқ Penguin утилиталарда (каталог: docs/ui-style-guide.md). */
        /* Alpine юклангунча x-cloak'ли қисмлар кўринмасин - FOUC ҳимояси */
        [x-cloak] { display:none !important; }
        @media (max-width: 768px) {
            /* iOS фокусда zoom'нинг олдини олади (Arbitr-121 7-банд).
               button РЎЙХАТДА ЙЎҚ (Arbitr-118): тугмалар zoom қилмайди,
               қатламсиз элемент қоидаси Penguin text-sm'ни босиб қўярди */
            input, select { font-size:16px; }
        }
        /* Чоп этиш нормализацияси - элементга ёзиб бўлмайдиган глобал
           қоидалар: жадвал фонлари канон фрагментида (shared/table.jte -
           ҳар чақирувчига print: такрорлаш ўрнига шу ерда бир жойда),
           @page эса утилита эмас. Қолган print ҳолатлари элементларда:
           print:hidden / hidden print:block / print:p-0 ва ҳ.к. */
        @media print {
            a { color:#000; text-decoration:none; }
            th, thead, tfoot { background:#fff !important; }
            tbody tr:nth-child(even), tbody tr:hover { background:#fff !important; }
            table { font-size:12px; }
            @page { margin:1.4cm; }
        }
    </style>
</head>
<%-- Контент зонаси Penguin токенларида (Arbitr-122): body бутун саҳифа
     фонини беради - тўқ режимда БУТУН саҳифа тўқ. Аралашда контент ОЧ
     қолади: dark: жуфтлар фақат html.dark'да ёнади (app.css
     @custom-variant - sb-dark қамрови фақат #sbnav/.sb-zone).
     Оқ канвас (Arbitr-123 қўшимча, фойдаланувчи қарори): light'да
     bg-surface - ажралиш бордер/thead/alt фонларда. --%>
<body class="bg-surface text-on-surface dark:bg-surface-dark dark:text-on-surface-dark print:bg-white print:text-black">
<%-- Arbitr-117 ҳолат маркази: nav - мобил drawer; rail - иконка режими;
     mode - кўриниш режими ('light'/'dark'/'mix', Arbitr-117в); pal -
     қидирув палитраси очиқлиги; grps - очиқ гуруҳлар рўйхати.
     rail/mode/grps Alpine Persist'да (спец талаби - сервер render'ида
     ҳолат йўқолмайди). grpOpen/railDesk: рейл-desktop'да гуруҳ рўйхати
     hover flyout, акс ҳолда grps бўйича collapse. x-init: жорий саҳифа
     гуруҳини автоочади (sidebar.jte охиридаги эрта FOUC скрипти
     data-active-group қўяди - Arbitr-129, биринчи paint'дан олдин) ва
     mode танловини <html> class'ларига боғлайди (биринчи қўллаш
     head'даги эрта скриптда - FOUC йўқ). applyMode эски 'darkMode'
     калитини ҳам синхрон ёзади - form.jte/login.jte FOUC скриптлари
     (бу картадан ташқари файллар) тўғри ишлайверсин: улар фақат тўлиқ
     Тўқ режимда dark бўлади - Аралашда контент оч, бу мосликнинг ўзи. --%>
<div x-data="{ nav: false,
               rail: $persist(false).as('sbRail'),
               mode: $persist('light').as('uiMode'),
               pal: false,
               grps: $persist([]).as('sbGrps'),
               toggleGrp(g) { this.grps.includes(g) ? this.grps = this.grps.filter(function(x) { return x !== g; }) : this.grps.push(g); },
               railDesk() { return this.rail && window.matchMedia('(min-width: 64rem)').matches; },
               grpOpen(g, h) { return this.railDesk() ? h : this.grps.includes(g); },
               applyMode(v) { document.documentElement.classList.toggle('dark', v === 'dark');
                              document.documentElement.classList.toggle('sb-dark', v === 'mix');
                              try { localStorage.setItem('darkMode', v === 'dark' ? 'true' : 'false'); } catch (e) {} } }"
     <%-- ДИҚҚАТ: x-init `let` билан бошланиши ШАРТ - Alpine фақат
          let/const/if бошланғичли ифодани статмент-блокка ўрайди
          (`var` билан SyntaxError бўлиб x-init жим ишламай қоларди) --%>
     x-init="let ag = document.documentElement.getAttribute('data-active-group'); if (ag && !grps.includes(ag)) grps.push(ag); applyMode(mode); $watch('mode', function(v) { applyMode(v); })">
    <%-- Arbitr-112: WHITE-LABEL бренд - логотип созланса ўша, бўлмаса
         fallback «AVERPO». LayoutInfo СТАТИК (Perms нақши,
         GlobalModelAttributes қўяди). --%>
    !{var lay = LayoutInfo.current();}
    <%-- Мобил drawer overlay - Penguin қолипи (сайдбар z-50 остида) --%>
    <div x-cloak x-show="nav" x-transition.opacity aria-hidden="true"
         class="fixed inset-0 z-40 bg-surface-dark/25 backdrop-blur-xs lg:hidden print:hidden"
         x-on:click="nav = false"></div>
    <div class="flex min-h-screen print:block">
        @template.layout.sidebar(msg = msg, perms = perms, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, lang = lang)
        <div class="flex min-w-0 flex-1 flex-col">
            <%-- Минимал мобил бар (Arbitr-117б): ФАҚАТ hamburger + white-label
                 бренд - drawer очиш учун (Penguin мобил қолипи). Desktop'да
                 топбар ЙЎҚ - контент тепадан бошланади (фойдаланувчи талаби 1).
                 «+ Янги» ва dark toggle sidebar'да (талаблар 2-3). --%>
            <%-- sb-zone - sidebar ОИЛАСИ маркери (Arbitr-117в): Аралаш
                 режимда nav ташқарисидаги бу бар ҳам тўқ бўлади
                 (@custom-variant dark қамрови, app.css изоҳи). --%>
            <header class="sb-zone sticky top-0 z-30 flex items-center gap-2 border-b border-outline bg-surface-alt px-3 py-2 lg:hidden dark:border-outline-dark dark:bg-surface-dark-alt print:hidden">
                <button type="button" x-on:click="nav = true" aria-label="${msg.lookup("common.menu")}"
                        class="cursor-pointer rounded-radius p-1.5 text-on-surface hover:bg-primary/5 hover:text-on-surface-strong focus:outline-hidden focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:bg-primary-dark/5 dark:hover:text-on-surface-dark-strong dark:focus-visible:outline-primary-dark">
                    @template.icons.menu2(cls = "size-6")
                </button>
                <div class="flex min-w-0 items-center gap-2">
                    @if(lay.hasBrandLogo())
                        <img src="/company/brand-logo" alt="${lay.companyName()}" class="max-h-7 max-w-[120px] object-contain">
                        <b class="truncate text-sm font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${lay.companyName()}</b>
                    @else
                        <b class="truncate text-sm font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${LayoutInfo.FALLBACK_BRAND}</b>
                    @endif
                </div>
            </header>
            <%-- main класси УСЛУБСИЗ зона маркери (смок/тест мосламалари
                 унга таянади) - услуб фақат утилиталарда --%>
            <main class="main min-w-0 flex-1 p-4 md:px-8 md:py-6 print:p-0">
                <%-- Arbitr-101 рефайнмент: паролни алмаштириш banner (066
                     must_change_password) - admin қўйган/reset'лаган паролдан
                     кейин ҳар саҳифада эслатади; user ЎЗ паролини алмаштиргач
                     (changeOwnPassword → флаг тушади) йўқолади. --%>
                @if(lay.mustChangePassword())
                    <div class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-radius border border-warning bg-warning/10 px-4 py-3 text-sm text-on-surface dark:text-on-surface-dark">
                        <span>${msg.localize("profile.mustChange")}</span>
                        <a href="/profile" class="whitespace-nowrap font-semibold text-on-surface underline dark:text-on-surface-dark">${msg.localize("profile.mustChange.action")}</a>
                    </div>
                @endif
                @if(helpKey != null)
                    <%-- Arbitr-079: саҳифа даражасидаги (?) - ягона интеграция
                         нуқтаси: чақирувчи фақат helpKey беради, тугма layout'дан
                         render бўлади. float:right - сарлавҳа қатори ўнг четида. --%>
                    <button type="button" class="helpbtn float-right mb-2 ml-3 mt-1 inline-flex size-[26px] shrink-0 cursor-pointer items-center justify-center rounded-full border border-current/40 bg-transparent p-0 align-middle text-[.9rem] font-semibold leading-none text-current/60 transition hover:border-primary hover:bg-primary hover:text-on-primary print:hidden dark:hover:border-primary-dark dark:hover:bg-primary-dark dark:hover:text-on-primary-dark"
                            data-help-title="${msg.lookup("help." + helpKey + ".title")}"
                            data-help-body="${msg.lookup("help." + helpKey + ".body")}"
                            aria-label="${msg.lookup("help.button")}">?</button>
                @endif
                ${content}
            </main>
        </div>
    </div>
    <%-- ======== Arbitr-117в: қидирув command-palette (реф:
         docs/penguin-refs/modal.html - Penguin modal + Averpo палитра
         мослашуви). Триггер sidebar'да, Ctrl/Cmd+K ва «/» ҳам очади
         (handleKey) - ФАҚАТ шу layout'да (form.jte'да атайлаб йўқ).
         Backdrop ДЕЯРЛИ ШАФФОФ (bg-black/10, blur'сиз) - фойдаланувчи
         талаби: орқадаги экран танилиб туриши шарт. HTMX оқими ва
         /search эндпойнти ЎЗГАРМАГАН (натижа ўша results.jte partial);
         JS'сиз fallback - триггер оддий <a href="/search">, Enter
         танловсиз бўлса форма /search'га боради.
         Панел контент оиласида: dark: жуфтлари фақат тўлиқ Тўқ режимда
         ёнади (Аралашда ҳам ОЧ - nav ташқарисида тургани учун sb-dark
         селектори унга тегмайди); натижа сатрлари search/group.jte
         partial'ида, ўша ерда ҳам шу семантика. --%>
    <div x-cloak x-show="pal" x-data="palette()" x-trap.noscroll="pal"
         x-on:keydown.escape.window="pal = false"
         x-on:keydown.window="handleKey($event)"
         x-on:htmx:after-swap="afterSwap()"
         x-effect="if (pal) { $nextTick(function () { $refs.input.focus(); $refs.input.select(); }); }"
         x-on:click.self="pal = false"
         class="fixed inset-0 z-[95] flex items-start justify-center bg-black/10 p-4 pt-[12vh] print:hidden"
         role="dialog" aria-modal="true" aria-label="${msg.lookup("search.title")}">
        <div x-show="pal"
             x-transition:enter="transition ease-out duration-150 motion-reduce:transition-opacity"
             x-transition:enter-start="opacity-0 scale-95" x-transition:enter-end="opacity-100 scale-100"
             class="flex w-full max-w-xl flex-col overflow-hidden rounded-radius border border-outline bg-surface text-on-surface shadow-xl dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark">
            <form class="relative flex items-center border-b border-outline dark:border-outline-dark" method="get" action="/search" role="search">
                @template.icons.search(cls = "pointer-events-none absolute left-3 size-5 text-on-surface/50 dark:text-on-surface-dark/50")
                <input type="search" name="q" x-ref="input" autocomplete="off"
                       class="w-full bg-transparent py-3 pl-11 pr-14 text-sm text-on-surface focus:outline-hidden dark:text-on-surface-dark"
                       placeholder="${msg.lookup("search.placeholder")}"
                       aria-label="${msg.lookup("search.title")}"
                       hx-get="/search"
                       hx-trigger="keyup[target.value.length>=2] changed delay:300ms"
                       hx-target="#searchResults" hx-swap="innerHTML"
                       x-on:keyup="onKeyup()"
                       x-on:keydown.arrow-down.prevent="move(1)"
                       x-on:keydown.arrow-up.prevent="move(-1)"
                       x-on:keydown.enter="onEnter($event)">
                <kbd class="pointer-events-none absolute right-3 rounded-radius border border-outline px-1.5 py-0.5 font-sans text-[10px] font-medium text-on-surface/60 dark:border-outline-dark dark:text-on-surface-dark/60">Esc</kbd>
            </form>
            <div id="searchResults" x-ref="results"
                 class="max-h-[min(60vh,480px)] overflow-y-auto overscroll-contain p-1 empty:hidden scrollbar-thin scrollbar-thumb-outline scrollbar-track-transparent"></div>
        </div>
    </div>
</div>
<%-- Arbitr-024: каталог формалари учун drawer - ҳар саҳифада тайёр туради --%>
@template.shared.drawer()
<%-- Arbitr-079: контекстли ёрдам диалоги + delegation - умумий partial
     (form.jte ҳам улайди - ҳужжат формаларидаги майдон (?) лари учун) --%>
@template.shared.helpDialog(msg = msg)
<script>
    // Фаол пункт (aria-current + data-active-group) sidebar.jte охиридаги
    // эрта FOUC скриптида (Arbitr-129) - биринчи paint'дан олдин ишлаши
    // шарт, шу боис бу ерда эмас.

    // T0: глобал row-click - data-href'ли қатор босилса ўша манзилга
    // ўтилади. Қатор ичидаги a/button/input/select/label/textarea
    // босилганда ишламайди - улар ўз вазифасини бажаради. document'га
    // delegation қилингани учун HTMX partial алмашганда ҳам ишлайверади.
    document.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;
        var row = e.target.closest('tr[data-href]');
        if (!row) return;
        // [data-rowmenu] - қатор ичидаги ⋮ dropdown: panel'нинг бўш жойи
        // босилганда ҳам навигация бўлмасин (Nargiza-007 P1a; Penguin
        // кўчишида .menu класси ўрнига белги - Arbitr-120)
        if (e.target.closest('a, button, input, select, label, textarea, [data-rowmenu]')) return;
        var href = row.getAttribute('data-href');
        // Arbitr-037: drawer каталогларда (contact/item) қатор ⋮ Таҳрир
        // билан бир хил - ўнг drawer'да очилади. data-drawer белгиси +
        // htmx бор бўлса #drawerBody'га юкланади (afterSwap drawer'ни
        // очади); акс ҳолда тўлиқ саҳифа (JS/htmx'сиз fallback).
        if (row.hasAttribute('data-drawer') && window.htmx) {
            window.htmx.ajax('GET', href, {target: '#drawerBody', swap: 'innerHTML'});
        } else {
            window.location.href = href;
        }
    });
</script>
<script>
    // Arbitr-117в: қидирув палитраси Alpine компоненти (эски сайдбар
    // searchBox ўрнида - inline dropdown хулқи олиб ташланди). HTMX
    // инпут натижаларни #searchResults'га юклайди; бу компонент фақат
    // кўриниш/клавиатура ҳолатини бошқаради (навигация - оддий <a>).
    // pal/nav ҳолати root x-data'да - Alpine ёзишни эгасига йўллайди.
    document.addEventListener('alpine:init', function () {
        Alpine.data('palette', function () {
            return {
                active: -1,        // ↑/↓ билан танланган сатр индекси
                // Инпутдаги сўров узунлиги (trim'дан кейин)
                queryLen: function () { return this.$refs.input.value.trim().length; },
                // 2 белгидан қисқарса эски натижани тозала (HTMX сўров
                // юбормайди - hx-trigger шарти ҳам >=2)
                onKeyup: function () {
                    if (this.queryLen() < 2) {
                        this.active = -1;
                        if (this.$refs.results) this.$refs.results.innerHTML = '';
                    }
                },
                // Жорий натижа линклари
                hits: function () {
                    return Array.from(this.$refs.results.querySelectorAll('a.search-hit'));
                },
                // ↑/↓ бўйлаб сурилиш (айланма)
                move: function (dir) {
                    var items = this.hits();
                    if (!items.length) return;
                    this.active = (this.active + dir + items.length) % items.length;
                    items.forEach(function (el, i) { el.classList.toggle('on', i === this.active); }, this);
                    items[this.active].scrollIntoView({ block: 'nearest' });
                },
                // Enter: танланган сатр очилади; йўқ бўлса форма /search'га
                // юборилади (JS'сиз fallback саҳифа)
                onEnter: function (e) {
                    var items = this.hits();
                    if (this.active >= 0 && items[this.active]) {
                        e.preventDefault();
                        window.location.href = items[this.active].getAttribute('href');
                    }
                },
                // HTMX натижа алмашгач: танловни нолла + мосликни белгила
                afterSwap: function () {
                    this.active = -1;
                    this.highlight(this.$refs.input.value.trim());
                },
                // Сўровга мос биринчи парчани <b> билан белгилаш (карта
                // 3-банд). textContent орқали - XSS хавфсиз (innerHTML'га
                // хом матн ёзилмайди).
                highlight: function (q) {
                    if (!q) return;
                    var low = q.toLowerCase();
                    this.$refs.results.querySelectorAll('.search-hit-label').forEach(function (el) {
                        var t = el.textContent;
                        var i = t.toLowerCase().indexOf(low);
                        if (i < 0) return;
                        el.textContent = '';
                        el.append(t.slice(0, i));
                        var b = document.createElement('b');
                        b.textContent = t.slice(i, i + q.length);
                        el.append(b, t.slice(i + q.length));
                    });
                },
                // Глобал клавишлар: Ctrl/Cmd+K очади (очиқ бўлса майдонга
                // фокус қайтаради - карта 5-банд); «/» ҳам очади (эски
                // одат, фақат инпут/textarea ташқарисида). Фақат шу
                // layout'да - form.jte бу скриптни уламайди.
                handleKey: function (e) {
                    var k = (e.key || '').toLowerCase();
                    if ((e.ctrlKey || e.metaKey) && k === 'k') {
                        e.preventDefault();
                        if (this.pal) {
                            this.$refs.input.focus();
                            this.$refs.input.select();
                        } else {
                            this.pal = true;
                            this.nav = false;
                        }
                        return;
                    }
                    if (k === '/' && !e.ctrlKey && !e.metaKey && !e.altKey) {
                        var t = e.target;
                        if (t && (/^(input|textarea|select)$/i.test(t.tagName) || t.isContentEditable)) return;
                        e.preventDefault();
                        this.pal = true;
                        this.nav = false;
                    }
                }
            };
        });
    });
</script>
<script src="/js/money-input.js?v=${Assets.version()}"></script>
<script src="/js/penguin-combobox.js?v=${Assets.version()}"></script>
<script src="/js/contact-currency.js?v=${Assets.version()}"></script>
<%-- Arbitr-097: курс блоки компоненти (prefill + кўриниш + ориентация) --%>
<script src="/js/rate-block.js?v=${Assets.version()}"></script>
<%-- Arbitr-144: сана майдонлари учун QBO ёрлиқлари (t/+/-/w/k/m/h/y/r) --%>
<script src="/js/date-hotkey.js?v=${Assets.version()}"></script>
</body>
</html>
