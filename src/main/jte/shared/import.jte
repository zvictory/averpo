@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.service.ExcelImportService.ImportError
@import java.util.List
@param Msg msg
@param String lang = "uz"
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param Boolean isAdmin = false
@param String message = null
@param String error = null
@param List<ImportError> errors = null

@template.layout.main(helpKey = "page.import", title = msg.lookup("import.title"), msg = msg, lang = lang, csrf = csrf, canEdit = canEdit, isAdmin = isAdmin, content = @`
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-2xl font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("import.heading")}</h1>
        <a href="/settings" class="text-primary underline dark:text-primary-dark">${msg.localize("unit.backToSettings")}</a>
    </div>

    <p class="mb-4 max-w-[640px] text-current/60">${msg.localize("import.intro")}</p>

    @template.shared.alert(message = message, error = error)

    <div style="max-width:640px;display:grid;gap:1rem;">
        <div class="grid gap-[.9rem] rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-5">
            <%-- 1-қадам: тайёр шаблонни юклаб олиш (арбитр ясаган .xlsx) --%>
            <div>
                <b>${msg.localize("import.step1")}</b>
                <p class="mb-2 mt-1 text-[.9rem] text-current/60">${msg.localize("import.templateHint")}</p>
                <a class="inline-block no-underline cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" href="/settings/import/template">${msg.localize("import.download")}</a>
            </div>

            <hr class="m-0 border-0 border-t border-outline dark:border-outline-dark">

            <%-- 2-қадам: тўлдирилган файлни юклаш (multipart, CSRF) --%>
            <form method="post" action="/settings/import" enctype="multipart/form-data"
                  style="display:grid;gap:.6rem;">
                @template.shared.csrf(csrf = csrf)
                <b>${msg.localize("import.step2")}</b>
                <%-- Alisa-050: чегаралар олдиндан кўринади (BR-IMP-001/004
                     server'да ҳақиқат манбаи бўлиб қолади) --%>
                <p class="m-0 text-[.9rem] text-current/60">${msg.localize("import.limits")}</p>
                <input type="file" name="file" accept=".xlsx" required
                       class="w-full rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                <button class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark" type="submit">${msg.localize("import.upload")}</button>
            </form>
        </div>

        <%-- Хатолар: сатр кесимида тўлиқ рўйхат (ҳеч нарса import қилинмади) --%>
        @if(errors != null && !errors.isEmpty())
            <div class="rounded-radius border border-danger bg-surface px-5 py-4 dark:bg-surface-dark">
                <p class="mb-2 font-semibold text-danger">${msg.localize("import.errorsTitle")}</p>
                @template.shared.table(c = @`
                    <thead>
                    <tr>
                        <th>${msg.localize("import.col.sheet")}</th>
                        <th>${msg.localize("import.col.row")}</th>
                        <th>${msg.localize("import.col.message")}</th>
                    </tr>
                    </thead>
                    <tbody>
                    @for(ImportError e : errors)
                        <tr>
                            <td>${e.sheet()}</td>
                            <td>${e.row() <= 0 ? "-" : String.valueOf(e.row())}</td>
                            <td>${e.message()}</td>
                        </tr>
                    @endfor
                    </tbody>
                `)
            </div>
        @endif
    </div>
`)
