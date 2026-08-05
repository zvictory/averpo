@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.domain.CompanySettings
@import com.averpo.erp.attachment.service.AttachmentService
@param CompanySettings settings
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null

@template.layout.main(helpKey = "page.company", title = msg.lookup("company.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("company.title")}</h1>
        <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("company.backToSettings")}</a>
    </div>

    @template.shared.alert(message = message, error = error)

    <%-- Ҳужжат логоси блоки (101 аватар нақши): preview/placeholder +
         юклаш/ўчириш. png/jpeg/webp, 2MB (BR-ATT-005/006/007). --%>
    <div class="mb-5 max-w-[560px] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        <b>${msg.localize("company.logo")}</b> @template.shared.help(key = "field.companyLogo", msg = msg)
        <div style="display:flex;align-items:center;gap:1rem;margin-top:.6rem;">
            @if(settings.getLogoAttachmentId() != null)
                <img src="/settings/company/logo" alt="${msg.localize("company.logo")}"
                     class="max-h-[72px] max-w-[120px] rounded-radius border border-outline bg-surface object-contain p-1 dark:border-outline-dark">
            @else
                <div class="flex size-[72px] shrink-0 items-center justify-center rounded-radius bg-surface-alt text-center text-[.8rem] text-current/50 dark:bg-surface-dark-alt">${msg.localize("company.logo.none")}</div>
            @endif
            <div style="display:grid;gap:.5rem;">
                <form method="post" action="/settings/company/logo" enctype="multipart/form-data" style="display:grid;gap:.5rem;">
                    @template.shared.csrf(csrf = csrf)
                    <input type="file" name="file" required accept="image/png,image/jpeg,image/webp">
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark justify-self-start" type="submit">${msg.localize("common.upload")}</button>
                </form>
                @if(settings.getLogoAttachmentId() != null)
                    <form method="post" action="/settings/company/logo/delete">
                        @template.shared.csrf(csrf = csrf)
                        <button class="cursor-pointer border-0 bg-transparent p-0 text-[.9rem] text-current/60 underline" type="submit">${msg.localize("company.logo.delete")}</button>
                    </form>
                @endif
            </div>
        </div>
        <span class="mt-2 block text-[.85rem] text-current/60">${msg.localize("company.logo.hint", AttachmentService.MAX_IMAGE_PX)}</span>
    </div>

    <%-- Бренд логоси блоки (рефайнмент банд 112.4): топбар WHITE-LABEL -
         ҳужжат логосидан ФАРҚли (бу UI топбар учун). png/jpeg/webp, 2MB,
         ўлчов диапазони (BR-ATT-007); топбарга ҚАТЪИЙ CSS max-height
         (қандай юкланса ҳам сиғади). Preview фони қора - топбар кўриниши. --%>
    <div class="mb-5 max-w-[560px] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        <b>${msg.localize("company.brandLogo")}</b> @template.shared.help(key = "field.brandLogo", msg = msg)
        <div style="display:flex;align-items:center;gap:1rem;margin-top:.6rem;">
            @if(settings.getBrandLogoAttachmentId() != null)
                <img src="/company/brand-logo" alt="${msg.localize("company.brandLogo")}"
                     class="max-h-10 max-w-[120px] rounded-radius border border-outline bg-surface-dark object-contain p-1.5 dark:border-outline-dark">
            @else
                <div class="flex h-10 w-[88px] shrink-0 items-center justify-center rounded-radius bg-surface-alt text-center text-xs text-current/50 dark:bg-surface-dark-alt">${msg.localize("company.brandLogo.none")}</div>
            @endif
            <div style="display:grid;gap:.5rem;">
                <form method="post" action="/settings/company/brand-logo" enctype="multipart/form-data" style="display:grid;gap:.5rem;">
                    @template.shared.csrf(csrf = csrf)
                    <input type="file" name="file" required accept="image/png,image/jpeg,image/webp">
                    <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark justify-self-start" type="submit">${msg.localize("common.upload")}</button>
                </form>
                @if(settings.getBrandLogoAttachmentId() != null)
                    <form method="post" action="/settings/company/brand-logo/delete">
                        @template.shared.csrf(csrf = csrf)
                        <button class="cursor-pointer border-0 bg-transparent p-0 text-[.9rem] text-current/60 underline" type="submit">${msg.localize("company.brandLogo.delete")}</button>
                    </form>
                @endif
            </div>
        </div>
        <span class="mt-2 block text-[.85rem] text-current/60">${msg.localize("company.brandLogo.hint", AttachmentService.MAX_IMAGE_PX)}</span>
    </div>

    <%-- Реквизит форма: Асосий / Реквизит / Раҳбар бўлимлари --%>
    <form method="post" action="/settings/company"
          class="grid max-w-[560px] gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
        @template.shared.csrf(csrf = csrf)

        <b>${msg.localize("company.sectionMain")}</b>
        <%-- Company name (рефайнмент банд 112.1): Созламада ҳам бор, лекин
             компания ўзлиги битта жойда тўлиқ бўлсин - айнан
             CompanySettings.name (required). --%>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("company.name")}
            <input name="name" value="${settings.getName()}" required
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("company.legalName")}
            <input name="legalName" value="${settings.getLegalName() == null ? "" : settings.getLegalName()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("company.address")}
            <textarea name="address" rows="2" class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">${settings.getAddress() == null ? "" : settings.getAddress()}</textarea>
        </label>
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:.9rem;">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("company.phone")}
                <input name="phone" value="${settings.getPhone() == null ? "" : settings.getPhone()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("company.email")}
                <input type="email" name="email" value="${settings.getEmail() == null ? "" : settings.getEmail()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("company.website")}
            <input name="website" value="${settings.getWebsite() == null ? "" : settings.getWebsite()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>

        <b style="margin-top:.5rem;">${msg.localize("company.sectionRequisites")}</b>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("company.taxId")}
            <input name="taxId" value="${settings.getTaxId() == null ? "" : settings.getTaxId()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
            ${msg.localize("company.bankName")}
            <input name="bankName" value="${settings.getBankName() == null ? "" : settings.getBankName()}"
                   class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
        </label>
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:.9rem;">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("company.bankAccount")}
                <input name="bankAccount" value="${settings.getBankAccount() == null ? "" : settings.getBankAccount()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("company.bankMfo")}
                <input name="bankMfo" value="${settings.getBankMfo() == null ? "" : settings.getBankMfo()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <b style="margin-top:.5rem;">${msg.localize("company.sectionDirector")}</b>
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:.9rem;">
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("company.directorName")}
                <input name="directorName" value="${settings.getDirectorName() == null ? "" : settings.getDirectorName()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
            <label class="flex flex-col gap-1 text-sm text-on-surface dark:text-on-surface-dark">
                ${msg.localize("company.directorPosition")}
                <input name="directorPosition" value="${settings.getDirectorPosition() == null ? "" : settings.getDirectorPosition()}"
                       class="mt-1 w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            </label>
        </div>

        <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("common.save")}</button>
    </form>
`)
