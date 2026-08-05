@import com.averpo.erp.web.Assets
@import gg.jte.Content
@param String title
@param String closeHref
@param Content content
@param Content actions
@param String lang = "uz"
@param com.averpo.erp.i18n.Msg msg = null

<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - AVERPO</title>
    <link rel="icon" type="image/svg+xml" href="/favicon.svg">
    <%-- DEC-117: Tailwind app.css (Inter + preflight). Dark танлови
         биринчи paint'дан олдин - main.jte билан бир калит ('darkMode');
         контент Penguin токенларида (DEC-122) - тўқда бутун
         саҳифа тўқ. --%>
    <script>
        if (localStorage.getItem('darkMode') === 'true') document.documentElement.classList.add('dark');
    </script>
    <%-- DEC-137: ?v=<build вақти> - main.jte билан бир хил жуфт --%>
    <link rel="stylesheet" href="/css/app.css?v=${Assets.version()}">
    <%-- Локал asset'лар - ERP ички тармоқ/офлайнда ҳам ишлайди --%>
    <script src="/vendor/htmx.min.js?v=${Assets.version()}"></script>
    <%-- Alpine 3 + расмий плагинлар (DEC-116): ҳаммаси defer, плагин
         файллари Alpine ядросидан ОЛДИН туради - Alpine ҳужжати шуни
         талаб қилади (плагин alpine:init'га ядро юкланишидан аввал уланади) --%>
    <script defer src="/js/vendor/alpine-mask.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine-collapse.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine-focus.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine-persist.min.js?v=${Assets.version()}"></script>
    <script defer src="/js/vendor/alpine.min.js?v=${Assets.version()}"></script>
    <style>
        /* DEC-122'дан кейинги ЯГОНА техник қолдиқ (main.jte билан
           бир хил жуфт): экран услублари тўлиқ Penguin утилиталарда. */
        /* Alpine юклангунча x-cloak'ли panel/майдонлар кўринмасин - FOUC
           ҳимояси (main.jte билан бир хил, UI-008) */
        [x-cloak] { display:none !important; }
        @media (max-width: 768px) {
            /* iOS фокусда zoom ҳимояси (DEC-121 7-банд);
               button рўйхатда йўқ (DEC-118) */
            input, select { font-size:16px; }
        }
    </style>
</head>
<%-- Оқ канвас (DEC-123 қўшимча, фойдаланувчи қарори): light'да
     body bg-surface - ажралиш бордер/thead/alt фонларда --%>
<body class="flex min-h-screen flex-col bg-surface text-on-surface dark:bg-surface-dark dark:text-on-surface-dark">
<%-- QBO transaction form паттерни: юқорида тўқ бар + ✕, пастда sticky
     actions бари - Penguin утилиталарда (DEC-121). formmain класси
     услубсиз зона МАРКЕРИ (смок/тест мосламалари учун). --%>
<div class="sticky top-0 z-40 flex items-center justify-between gap-3 bg-surface-dark px-5 py-3 text-on-surface-dark-strong">
    <b class="text-[1.05rem]">${title}</b>
    <a href="${closeHref}" class="cursor-pointer rounded-radius px-2 py-1 text-[1.4rem] leading-none text-on-surface-dark no-underline transition hover:bg-surface-dark-alt hover:text-on-surface-dark-strong" aria-label="${msg != null ? msg.lookup("common.close") : "Ёпиш"}">✕</a>
</div>
<%-- bg-surface + dark жуфти: форма зонаси тўқ режимда ЯХЛИТ тўқ
     (карта 3-банд) - боди оч қолса ҳам майдон/label'лар ўз сиртида --%>
<main class="formmain mx-auto w-full max-w-[1100px] flex-1 bg-surface px-5 pb-8 pt-6 max-md:px-3 max-md:pb-5 max-md:pt-4 dark:bg-surface-dark">
    ${content}
</main>
<footer class="sticky bottom-0 z-40 flex flex-wrap items-center justify-between gap-3 bg-surface-dark px-5 py-3">
    ${actions}
</footer>
<%-- DEC-079: ҳужжат формаларидаги майдон (?) тугмалари учун ёрдам
     диалоги + delegation - main.jte билан умумий partial (msg бу
     layout'да null бўлиши мумкин - partial ўзи ҳимояланган) --%>
@template.shared.helpDialog(msg = msg)
<script src="/js/money-input.js?v=${Assets.version()}"></script>
<script src="/js/penguin-combobox.js?v=${Assets.version()}"></script>
<script src="/js/contact-currency.js?v=${Assets.version()}"></script>
<%-- DEC-097: курс блоки компоненти (prefill + кўриниш + ориентация) --%>
<script src="/js/rate-block.js?v=${Assets.version()}"></script>
<%-- DEC-144: сана майдонлари учун QBO ёрлиқлари (t/+/-/w/k/m/h/y/r) --%>
<script src="/js/date-hotkey.js?v=${Assets.version()}"></script>
</body>
</html>
