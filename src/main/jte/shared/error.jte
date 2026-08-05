@import com.averpo.erp.i18n.Msg
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param String errorMessage = null
<%-- Статусга мос сарлавҳа - ErrorController/GlobalExceptionHandler
     titleFor(status) орқали беради; бермаса умумий «Хатолик юз берди». --%>
@param String errorTitle = null
<%-- DEC-127: катта статус код ва статусга мос иконка учун. Иккала
     чақирувчи ҳам беради; default 500 - эски чақирувчи қолса саҳифа
     синмасин. --%>
@param int status = 500

<%-- DEC-127 полиши: марказда иконка + катта код + сарлавҳа +
     тушунтириш + иккита амал. Layout ичида қолади (авторизацияли
     фойдаланувчи sidebar'ини йўқотмайди), аноним render ҳам ишлайди
     (096 хулқи: /error permitAll, Perms бўш). Тушунтириш нейтрал хира
     матн - аввалги қизил рамка 404/403 каби оддий ҳолатлар учун ортиқча
     тревога эди; BR хабарлари ҳам шу услубда ўқилади. Stack trace ёки
     exception тафсилоти бу саҳифага ҲЕЧ ҚАЧОН берилмайди (handler'лар
     фақат тайёр i18n/BR матн узатади). --%>
!{var shownTitle = errorTitle != null ? errorTitle : msg.lookup("error.title");}
@template.layout.main(title = shownTitle, msg = msg, lang = lang, csrf = csrf, content = @`
    <div class="mx-auto flex max-w-[560px] flex-col items-center px-4 py-12 text-center md:py-16">
        <%-- Статусга мос Tabler иконка (3.44.0 outline, таначалар айнан -
             icons/ канони: viewBox 24, stroke currentColor, қалинлик 2).
             Декоратив: сарлавҳа матни бор - aria-hidden. --%>
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="size-16 text-on-surface/40 dark:text-on-surface-dark/40">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            @if(status == 404)
                <%-- file-search: ҳужжат/саҳифа топилмади --%>
                <path d="M14 3v4a1 1 0 0 0 1 1h4"/><path d="M12 21h-5a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v4.5"/><path d="M14 17.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"/><path d="M18.5 19.5l2.5 2.5"/>
            @elseif(status == 403)
                <%-- lock: рухсат ёпиқ --%>
                <path d="M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6"/><path d="M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0"/><path d="M8 11v-4a4 4 0 1 1 8 0v4"/>
            @elseif(status == 400)
                <%-- alert-triangle: сўровнинг ўзи нотўғри --%>
                <path d="M12 9v4"/><path d="M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0"/><path d="M12 16h.01"/>
            @elseif(status == 405)
                <%-- ban: усул тақиқланган --%>
                <path d="M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"/><path d="M5.7 5.7l12.6 12.6"/>
            @else
                <%-- exclamation-circle: умумий/server хатоси (500 ва бошқалар) --%>
                <path d="M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"/><path d="M12 9v4"/><path d="M12 16v.01"/>
            @endif
        </svg>
        <p class="mt-4 text-6xl font-bold tracking-tight text-primary dark:text-primary-dark">${status}</p>
        <h1 class="mt-3 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${shownTitle}</h1>
        @if(errorMessage != null)
            <p class="mt-2 text-on-surface/70 dark:text-on-surface-dark/70">${errorMessage}</p>
        @endif
        <div class="mt-8 flex flex-wrap items-center justify-center gap-3">
            <a href="/" class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("error.back")}</a>
            <%-- Орқага - ghost'нинг контент юзаси мослашуви: канон ghost
                 фақат қора барлар учун (style-guide 4-бўлим), оч фонда
                 унинг оч матни кўринмас эди - нейтрал outline/матн
                 токенлари билан, иккала режимда ўқилади. JS'сиз ҳолатда
                 тугма шунчаки иш бермайди - асосий йўл (primary) доим бор. --%>
            <button type="button" onclick="history.back()" class="cursor-pointer whitespace-nowrap rounded-radius border border-outline bg-transparent px-4 py-2 text-center text-sm font-medium tracking-wide text-on-surface transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 dark:border-outline-dark dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">${msg.localize("error.goBack")}</button>
        </div>
    </div>
`)
