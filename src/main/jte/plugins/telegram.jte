@import com.averpo.erp.i18n.Msg
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null
<%-- Маскаланган токен (12345:AB●●●●) ёки null - бот созланмаган.
     Очиқ токен бу саҳифага ҲЕЧ ҚАЧОН келмайди (TelegramService.maskedToken). --%>
@param String maskedToken = null
@param String botUsername = null

@template.layout.main(helpKey = "page.telegram", title = msg.lookup("telegram.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("telegram.title")}</h1>
        <a href="/settings/plugins" class="text-primary underline dark:text-primary-dark">${msg.localize("plugin.title")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <p class="mb-4 max-w-[560px] text-sm text-current/60">${msg.localize("telegram.intro")}</p>

    <%-- Жорий ҳолат картаси: созланган бўлса бот номи + маска --%>
    @if(maskedToken != null)
        <div class="mb-4 max-w-[560px] rounded-radius border border-outline bg-surface-alt p-4 text-on-surface dark:border-outline-dark dark:bg-surface-dark-alt dark:text-on-surface-dark">
            <div class="flex flex-wrap items-center gap-2">
                @template.shared.badge(value = "ENABLED", msg = msg, family = "plugin.state")
                <b class="text-on-surface-strong dark:text-on-surface-dark-strong">@${botUsername}</b>
            </div>
            <%-- Маска - фойдаланувчи «ўша токенми?» деб таниши учун; тўлиқ
                 қиймат экранга ҳеч қачон чиқмайди (спец хавфсизлик талаби) --%>
            <p class="mt-2 font-mono text-sm text-current/60">${maskedToken}</p>
            <form method="post" action="/settings/telegram/delete" class="mt-3"
                  onsubmit="return confirm('${msg.lookup("telegram.deleteConfirm")}');">
                @template.shared.csrf(csrf = csrf)
                <button type="submit"
                        class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">
                    ${msg.localize("telegram.delete")}
                </button>
            </form>
        </div>
    @endif

    <%-- Токен формаси: янги киритиш ёки алмаштириш --%>
    <form method="post" action="/settings/telegram"
          class="grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)
        <b>${msg.localize(maskedToken == null ? "telegram.form.title" : "telegram.form.replaceTitle")}</b>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("telegram.form.token")}
            <%-- autocomplete=off - браузер сирни сақлаб қолмасин;
                 type=password эмас (админ киритаётганини кўриб турсин -
                 бу ерда сир экранга ЯНГИ киритилмоқда, ўқилмаяпти) --%>
            <input name="token" required autocomplete="off" spellcheck="false"
                   placeholder="123456789:AA..."
                   class="mt-1 w-full font-mono rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <p class="-mt-2 text-xs text-current/60">${msg.localize("telegram.form.tokenHint")}</p>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("telegram.form.verifySave")}</button>
    </form>
`)
