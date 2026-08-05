@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.security.domain.AppUser
@import com.averpo.erp.security.domain.Gender
@import com.averpo.erp.attachment.service.AttachmentService
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null
@param AppUser user
@param Gender[] genders
@param String employeeName = null
<%-- Telegram блоки (DEC-103): enabled - плагин гейти (DEC-113),
     configured - бот токени созланган; link - «улаш» босилгандан кейин
     flash билан келган бир марталик код + deep link --%>
@param Boolean telegramEnabled = false
@param Boolean telegramConfigured = false
@param com.averpo.erp.plugins.telegram.service.TelegramService.LinkInfo telegramLink = null

@template.layout.main(helpKey = "page.profile", title = msg.lookup("profile.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <h1 class="mb-4 text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("profile.title")}</h1>

    @template.shared.alert(message = message, error = error)

    <%-- Ким + аватар виджет (рефайнмент банд 2): аватарнинг ЎЗи контрол -
         бурчагида camera тугма (расм танлаш → авто юклаш), расм бор бўлса
         остида «ўчириш». Алоҳида «Choose file + Юклаш» формаси ЙЎҚ. --%>
    <div class="mb-5 max-w-[560px] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        <div style="display:flex;align-items:center;gap:1rem;">
            <div style="position:relative;width:72px;height:72px;flex-shrink:0;">
                @if(user.getProfileImageId() != null)
                    <img src="/profile/image" alt="${msg.localize("profile.avatar")}"
                         class="size-[72px] rounded-full border border-outline object-cover dark:border-outline-dark">
                @else
                    <%-- Placeholder: displayName биринчи ҳарфи (BR-USR-004 - бўш эмас) --%>
                    <div class="flex size-[72px] items-center justify-center rounded-full bg-primary text-[1.8rem] font-semibold text-on-primary dark:bg-primary-dark dark:text-on-primary-dark">
                        ${user.getDisplayName().substring(0, 1).toUpperCase()}
                    </div>
                @endif
                <%-- camera тугма (бурчакда) - расм танлаш → форма авто submit
                     (Alpine x-on:change; JS ўчиқ бўлса ҳам input+submit ишлайди) --%>
                <form method="post" action="/profile/image" enctype="multipart/form-data" style="margin:0;">
                    @template.shared.csrf(csrf = csrf)
                    <label title="${msg.localize("profile.avatar.change")}"
                           class="absolute -bottom-0.5 -right-0.5 flex size-[26px] cursor-pointer items-center justify-center rounded-full border-2 border-surface bg-primary text-on-primary shadow dark:border-surface-dark dark:bg-primary-dark dark:text-on-primary-dark">
                        <input type="file" name="file" accept="image/png,image/jpeg,image/webp"
                               x-on:change="$el.form.submit()" class="hidden">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
                    </label>
                </form>
            </div>
            <div style="min-width:0;">
                <div style="font-size:1.1rem;font-weight:600;">${user.getDisplayName()}</div>
                <div class="text-sm text-current/60">${user.getUsername()}</div>
                <div class="mt-1 text-[.85rem] text-current/60">${msg.localize("user.role." + user.getRole().name())}</div>
                <%-- Уланган ходим (4-бўлим) - read-only; super-admin /users'да
                     ўрнатади, фойдаланувчи ўзи ўзгартира олмайди --%>
                @if(employeeName != null)
                    <div class="mt-1 text-[.85rem] text-current/60">${msg.localize("profile.employee")}: ${employeeName}</div>
                @endif
                @if(user.getProfileImageId() != null)
                    <form method="post" action="/profile/image/delete" style="margin-top:.35rem;">
                        @template.shared.csrf(csrf = csrf)
                        <button class="cursor-pointer border-0 bg-transparent p-0 text-[.82rem] text-current/60 underline" type="submit">${msg.localize("profile.avatar.delete")}</button>
                    </form>
                @endif
            </div>
        </div>
        <span class="mt-2.5 block text-[.8rem] text-current/60">${msg.localize("profile.avatar.hint", AttachmentService.MAX_IMAGE_PX)}</span>
    </div>

    <%-- Маълумотлар: кўрсатиладиган ном (DEC-148, required - BR-USR-004)
         + ихтиёрий шахсий майдонлар. Битта форма, битта «Сақлаш» - ном
         ўзгарса sidebar/топбар кейинги render'да ўзи янгиланади --%>
    <form method="post" action="/profile"
          class="mb-5 grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)
        <b>${msg.localize("profile.personalInfo")}</b>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.displayName")}
            <input name="displayName" required maxlength="255" value="${user.getDisplayName()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.email")}
            <input type="email" name="email" value="${user.getEmail() == null ? "" : user.getEmail()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.gender")}
            <select name="gender" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <option value="" selected="${user.getGender() == null}">${msg.localize("profile.gender.unspecified")}</option>
                @for(Gender g : genders)
                    <option value="${g.name()}" selected="${g == user.getGender()}">${msg.localize(g.titleKey())}</option>
                @endfor
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.birthdate")}
            <input type="date" name="birthdate"
                   value="${user.getBirthdate() == null ? null : user.getBirthdate().toString()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.phone")}
            <input name="phone" value="${user.getPhone() == null ? "" : user.getPhone()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
    </form>

    <%-- Парол алмаштириш (эски /profile/password оқими шу саҳифага кўчди) --%>
    <form method="post" action="/profile/password"
          class="grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)
        <b>${msg.localize("profile.password.title")}</b>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.password.old")}
            <input type="password" name="oldPassword" required autocomplete="current-password"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.password.new")}
            <input type="password" name="newPassword" required autocomplete="new-password"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("profile.password.confirm")}
            <input type="password" name="newPasswordConfirm" required autocomplete="new-password"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
    </form>

    <%-- Telegram блоки (DEC-103) - ФАҚАТ плагин ёқиқда (DEC-113
         гейти; ўчиқда route'лар ҳам 404 беради, UI яшириш кифоя эмас) --%>
    @if(telegramEnabled)
        <div class="mt-5 grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
            <b>${msg.localize("telegram.profile.title")}</b>
            @if(!telegramConfigured)
                <%-- Бот созланмаган: оддий фойдаланувчи ҳеч нарса қила
                     олмайди - SUPER_ADMIN Созламаларда улайди (спец) --%>
                <p class="text-sm text-current/60">${msg.localize("telegram.profile.notConfigured")}</p>
            @elseif(user.telegramLinked())
                <p class="text-sm text-on-surface dark:text-on-surface-dark">
                    ${msg.localize("telegram.profile.linked")}:
                    <b class="text-on-surface-strong dark:text-on-surface-dark-strong">@${user.getTelegramUsername() == null ? user.getTelegramChatId().toString() : user.getTelegramUsername()}</b>
                </p>
                <form method="post" action="/profile/telegram/unlink">
                    @template.shared.csrf(csrf = csrf)
                    <button type="submit"
                            class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">
                        ${msg.localize("telegram.profile.unlink")}
                    </button>
                </form>
            @elseif(telegramLink != null)
                <%-- Код яратилди: линк 10 дақиқа амал қилади (flash -
                     саҳифа янгиланса йўқолади, қайта «улаш» босилади) --%>
                <p class="text-sm text-on-surface dark:text-on-surface-dark">${msg.localize("telegram.profile.linkReady")}</p>
                <a href="${telegramLink.deepLink()}" target="_blank" rel="noopener"
                   class="inline-block w-fit no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">
                    ${msg.localize("telegram.profile.openBot")} →
                </a>
                <p class="text-xs text-current/60">${msg.localize("telegram.profile.linkHint")}</p>
            @else
                <p class="text-sm text-current/60">${msg.localize("telegram.profile.notLinked")}</p>
                <form method="post" action="/profile/telegram/link">
                    @template.shared.csrf(csrf = csrf)
                    <button type="submit"
                            class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">
                        ${msg.localize("telegram.profile.link")}
                    </button>
                </form>
            @endif
        </div>
    @endif
`)
