@import com.averpo.erp.i18n.Msg
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String companyName
@param String error = null

@template.layout.main(helpKey = "page.factoryReset", title = msg.lookup("reset.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div style="max-width:560px;">
        <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("reset.cancel")}</a>
        <h1 class="mb-4 mt-3 text-2xl font-bold text-danger">${msg.localize("reset.heading")}</h1>

        <%-- 1-босқич danger: нима ўчиши ТЎЛИҚ рўйхат билан огоҳлантирилади --%>
        <div class="rounded-radius border-2 border-danger/40 bg-danger/10 px-5 py-4">
            <p class="mb-2.5 font-bold text-danger">${msg.localize("reset.warning")}</p>
            <ul class="mb-3.5 ml-4 list-disc pl-1 leading-[1.7] text-danger">
                <li>${msg.localize("reset.warning.docs")}</li>
                <li>${msg.localize("reset.warning.masters")}</li>
                <li>${msg.localize("reset.warning.attachments")}</li>
                <li>${msg.localize("reset.warning.users")}</li>
            </ul>
            <p class="m-0 rounded-radius border border-success/30 bg-success/10 px-3 py-2 text-[.9rem] text-success">
                ${msg.localize("reset.keeps")}
            </p>
        </div>

        @if(error != null)
            <p class="mt-4 font-semibold text-danger">${error}</p>
        @endif

        <%-- Admin паролини қайта киритиш (BR-RST-001, AuthenticationManager) --%>
        <form method="post" action="/settings/reset"
              class="mt-4 grid max-w-[400px] gap-3 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark px-5 py-4">
            @template.shared.csrf(csrf = csrf)
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("reset.password")}
                <input type="password" name="password" required autocomplete="current-password"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <button type="submit"
                    class="cursor-pointer rounded-radius border-0 bg-danger px-4 py-2 font-semibold text-on-danger">
                ${msg.localize("reset.continue")}
            </button>
        </form>
    </div>
`)
