@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.web.Assets
@param Msg msg
@param String lang = "uz"
@param boolean loginError = false
@param boolean loginLocked = false
@param boolean loggedOut = false
<%-- Arbitr-096: сессия муддати тугаб CSRF токени эскирганда
     accessDeniedHandler шу параметр билан қайтаради --%>
@param boolean sessionExpired = false
@param org.springframework.security.web.csrf.CsrfToken csrf = null
<%-- Dev профилида LoginController тўлдиради - форма тайёр келади,
     фойдаланувчи фақат «Кириш»ни босади. Prod'да доим null (JTE null
     attribute'ни бутунлай чиқармайди - парол HTML'га тушмайди). --%>
@param String devUsername = null
@param String devPassword = null

<%-- Arbitr-117: биринчи Penguin экрани - card қолипи + text-input +
     primary button (реф: docs/penguin-refs/), arctic палитра, Inter,
     иккала mode. Alpine шарт эмас - dark сақланган танловдан эрта inline
     скрипт билан қўлланади (toggle топбарда, у авторизациядан кейин). --%>
<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg.lookup("login.title")} - AVERPO</title>
    <link rel="icon" type="image/svg+xml" href="/favicon.svg">
    <script>
        if (localStorage.getItem('darkMode') === 'true') document.documentElement.classList.add('dark');
    </script>
    <%-- Arbitr-137: ?v=<build вақти> - main.jte билан бир хил жуфт --%>
    <link rel="stylesheet" href="/css/app.css?v=${Assets.version()}">
</head>
<body class="flex min-h-svh items-center justify-center bg-surface-alt p-4 text-on-surface dark:bg-surface-dark dark:text-on-surface-dark">
<div class="w-full max-w-sm rounded-radius border border-outline bg-surface p-8 dark:border-outline-dark dark:bg-surface-dark-alt">
    <%-- Arbitr-143: вендор логоси (h1 матн ўрнида) - икки <img> ишончли
         алмашуви (light .dark класс localStorage'дан, 30-қатор inline
         скрипт): light `dark:hidden`, dark `hidden dark:block`. Ёзув оч
         фонда навй (#001743), тўқ фонда оч (#E5E7EB); иконка ранглари
         иккисида ҳам. ?v= кэш-бузиш (Arbitr-137 қолипи). --%>
    <h1>
        <img src="/img/averpo-logo-light.svg?v=${Assets.version()}" alt="Averpo" class="mx-auto block h-11 w-auto max-w-[180px] dark:hidden">
        <img src="/img/averpo-logo-dark.svg?v=${Assets.version()}" alt="Averpo" class="mx-auto hidden h-11 w-auto max-w-[180px] dark:block">
    </h1>
    <p class="mb-6 mt-2 text-center text-sm text-on-surface/70 dark:text-on-surface-dark/70">${msg.localize("login.subtitle")}</p>

    @if(loginError)
        <p class="mb-4 rounded-radius border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
            ${msg.localize("login.error")}
        </p>
    @endif
    @if(loginLocked)
        <%-- BR-USR-009: lockout алоҳида хабар - ?error умумийлигича қолади --%>
        <p class="mb-4 rounded-radius border border-warning/50 bg-warning/10 px-3 py-2 text-sm text-on-surface-strong dark:text-on-surface-dark-strong">
            ${msg.localize("login.locked")}
        </p>
    @endif
    @if(loggedOut)
        <p class="mb-4 rounded-radius border border-success/40 bg-success/10 px-3 py-2 text-sm text-success">${msg.localize("login.loggedOut")}</p>
    @endif
    @if(sessionExpired)
        <%-- Arbitr-096: муддат тугади - нейтрал хабар, хато эмас --%>
        <p class="mb-4 rounded-radius border border-outline bg-surface-alt px-3 py-2 text-sm dark:border-outline-dark dark:bg-surface-dark/50">
            ${msg.localize("login.expired")}
        </p>
    @endif

    <form method="post" action="/login">
        @template.shared.csrf(csrf = csrf)
        <label class="mb-4 flex w-full flex-col gap-1 text-sm">
            <span class="w-fit pl-0.5">${msg.localize("login.username")}</span>
            <%-- text-base (16px) - iOS фокусда zoom қилмасин --%>
            <input name="username" required autofocus autocomplete="username" value="${devUsername}"
                   class="w-full rounded-radius border border-outline bg-surface-alt px-2.5 py-2 text-base text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:bg-surface-dark/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="mb-4 flex w-full flex-col gap-1 text-sm">
            <span class="w-fit pl-0.5">${msg.localize("login.password")}</span>
            <input type="password" name="password" required autocomplete="current-password" value="${devPassword}"
                   class="w-full rounded-radius border border-outline bg-surface-alt px-2.5 py-2 text-base text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:bg-surface-dark/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <%-- «Мени эсла» (Arbitr-141): name="remember-me" - Spring default
             параметри (SecurityConfig .rememberMe). Белгиланса value="true"
             юборилади, белгиланмаса умуман юборилмайди (checkbox семантикаси) --%>
        <label class="mb-5 flex w-full cursor-pointer items-center gap-2 text-sm">
            <input type="checkbox" name="remember-me" value="true"
                   class="size-4 accent-primary dark:accent-primary-dark">
            <span>${msg.localize("login.rememberMe")}</span>
        </label>
        <button type="submit"
                class="w-full cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("login.submit")}</button>
    </form>

    <div class="mt-6 flex items-center justify-center gap-1 text-sm">
        <a href="?lang=uz" class="${lang.equals("uz") ? "rounded-radius px-2 py-1 font-semibold text-primary dark:text-primary-dark" : "rounded-radius px-2 py-1 text-on-surface/70 hover:text-on-surface-strong dark:text-on-surface-dark/70 dark:hover:text-on-surface-dark-strong"}">ЎЗ</a>
        <a href="?lang=ru" class="${lang.equals("ru") ? "rounded-radius px-2 py-1 font-semibold text-primary dark:text-primary-dark" : "rounded-radius px-2 py-1 text-on-surface/70 hover:text-on-surface-strong dark:text-on-surface-dark/70 dark:hover:text-on-surface-dark-strong"}">РУ</a>
        <a href="?lang=en" class="${lang.equals("en") ? "rounded-radius px-2 py-1 font-semibold text-primary dark:text-primary-dark" : "rounded-radius px-2 py-1 text-on-surface/70 hover:text-on-surface-strong dark:text-on-surface-dark/70 dark:hover:text-on-surface-dark-strong"}">EN</a>
    </div>
</div>
</body>
</html>
