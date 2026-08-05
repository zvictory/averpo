@import com.averpo.erp.i18n.Msg
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String companyName
@param String error = null

@template.layout.main(title = msg.lookup("reset.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div style="max-width:560px;">
        <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("reset.cancel")}</a>
        <h1 class="mb-4 mt-3 text-2xl font-bold text-danger">${msg.localize("reset.step2.heading")}</h1>

        <%-- 2-босқич (пароль тўғри бўлгандан кейин): якуний тасдиқ --%>
        <div class="rounded-radius border-2 border-danger/40 bg-danger/10 px-5 py-4">
            <p class="m-0 font-bold text-danger">${msg.localize("reset.step2.body")}</p>
        </div>

        @if(error != null)
            <p class="mt-4 font-semibold text-danger">${error}</p>
        @endif

        <%-- Тасдиқ матни: компания номини айнан ёзиш талаб қилинади (BR-RST-002) --%>
        <form method="post" action="/settings/reset/confirm"
              class="mt-4 grid max-w-[400px] gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("reset.confirmLabel")}
                <div class="my-1 text-[.9rem] text-current/60">${msg.localize("reset.confirmPrompt")}</div>
                <div style="font-weight:700;margin-bottom:.4rem;">${companyName}</div>
                <input name="confirmName" required autocomplete="off"
                       class="w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <button type="submit"
                    class="cursor-pointer rounded-radius border-0 bg-danger px-4 py-2 font-bold text-on-danger">
                ${msg.localize("reset.finalButton")}
            </button>
        </form>
    </div>
`)
