@import com.averpo.erp.attachment.domain.Attachment
@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.shared.web.Fmt
@import java.time.ZoneId
@import java.util.List
@import java.util.Map
@import java.util.UUID
@param String documentType
@param UUID documentId
@param List<Attachment> attachments
@param Map<UUID, String> uploaderNames = java.util.Map.of()
@param ZoneId zoneId = java.time.ZoneOffset.UTC
@param Msg msg
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param Boolean canEdit = false
@param String attError = null

<%-- DEC-094: inline кўрсатишга хавфсиз MIME турлари - «Кўриш» тугмаси
     ФАҚАТ шуларда чиқади. AttachmentController.INLINE_VIEWABLE_TYPES билан
     бир хил (himoya-in-depth: бу рўйхат UI тугмасини бошқаради, сервер
     view endpoint'и барибир текширади - SVG ва бошқа тур ҳеч қачон inline
     эмас, XSS). Регистр/параметр нормаллаштирилиб солиштирилади. --%>
!{var INLINE_VIEWABLE = java.util.Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");}

<%-- «Иловалар» бўлими (attachments.md): ҳар транзакция КЎРИШ экранига
     hx-get билан юкланадиган фрагмент. Юклаш/ўчириш hx-post бўлимни
     жойида (outerHTML) янгилайди - бутун саҳифа қайта юкланмайди.
     Бўлим id = attachments-{documentId}: hx-target шуни топади. --%>
<%-- margin-top: сторно/амал блоклари пастки margin'сиз - бўлим уларга
     ёпишиб қолмасин (2026-07-11); олдинги блокда margin-bottom бўлса
     collapse қилади - қўш-масофа бўлмайди. --%>
<section id="attachments-${documentId.toString()}"
         class="my-4 rounded-radius border border-outline bg-surface dark:border-outline-dark dark:bg-surface-dark p-4">
    <%-- DEC-128: юклаш бўлим тепасида - иловалар кўп бўлса ҳам доим
         кўринишда. Форма сарлавҳа ТАГИДА, чап томонда (кенг экранда ўнг
         четга кетиб қолмасин); 375px'да input тўлиқ энли қаторга ўралади
         (flex-wrap + max-md:flex-1). Юклаш механикаси: multipart, CSRF
         hidden token, hx-encoding файлни FormData билан юборади (htmx
         multipart); paste-JS (қуйида) формани section ичидан enctype
         бўйича топади - жойлашув унга таъсир қилмайди. --%>
    <h3 class="mb-2 text-lg font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${msg.localize("att.title")}</h3>

    @if(canEdit != null && canEdit)
        <form method="post" action="/attachments/${documentType}/${documentId.toString()}"
              enctype="multipart/form-data"
              hx-post="/attachments/${documentType}/${documentId.toString()}"
              hx-target="#attachments-${documentId.toString()}" hx-swap="outerHTML"
              hx-encoding="multipart/form-data"
              class="flex min-w-0 flex-wrap items-center gap-2">
            @template.shared.csrf(csrf = csrf)
            <input type="file" name="file" required class="w-56 min-w-0 rounded-radius border border-outline bg-surface-alt px-2 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 max-md:flex-1 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-primary bg-primary px-4 py-2 text-center text-sm font-medium tracking-wide text-on-primary transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75 dark:border-primary-dark dark:bg-primary-dark dark:text-on-primary-dark dark:focus-visible:outline-primary-dark">${msg.localize("att.upload")}</button>
        </form>

        <%-- Ҳинтлар (att.hint + DEC-094 clipboard paste) - форма остида
             ихчам: paste'ни қуйидаги JS шу бўлимда ушлаб формага юборади. --%>
        <div class="mb-3 mt-1.5">
            <p class="text-xs text-current/60">${msg.localize("att.hint")}</p>
            <p class="mt-0.5 text-xs text-current/60">${msg.localize("att.pasteHint")}</p>
        </div>
    @endif

    @if(attError != null)
        <p class="mb-3 text-danger">${attError}</p>
    @endif

    @if(attachments.isEmpty())
        <p class="text-current/60">${msg.localize("att.empty")}</p>
    @else
        <%-- DEC-128: кўп иловада бўлим чўзилмасин - жадвал ўрами ички
             вертикал скроллда (max-h-80 + sidebar scrollbar канони),
             юклаш формаси доим кўринишда қолади. --%>
        @template.shared.table(cls = "min-w-[420px]", wrap = "max-h-80 overflow-y-auto scrollbar-thin scrollbar-thumb-outline scrollbar-track-transparent dark:scrollbar-thumb-outline-dark", c = @`
            <thead>
            <tr>
                <th>${msg.localize("att.col.name")}</th>
                <th class="text-right">${msg.localize("att.col.size")}</th>
                <th>${msg.localize("att.col.date")}</th>
                <th>${msg.localize("att.col.who")}</th>
                @if(canEdit != null && canEdit)
                    <th></th>
                @endif
            </tr>
            </thead>
            <tbody>
            @for(Attachment a : attachments)
                <%-- Сақланган MIME нормаллаштирилади (параметрсиз, кичик
                     регистр) - «Кўриш» тугмаси фақат хавфсиз турда чиқади. --%>
                !{var ct = a.getContentType() == null ? "" : a.getContentType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);}
                !{var viewable = INLINE_VIEWABLE.contains(ct);}
                <tr>
                    <td>
                        <a href="/attachments/${a.getId().toString()}/download"
                           class="text-primary underline dark:text-primary-dark">${a.getOriginalName()}</a>
                        @if(viewable)
                            <%-- Модалда очиш (JS delegation) - PDF iframe, расм img. --%>
                            <button type="button" class="att-view-btn ml-2 cursor-pointer border-0 bg-transparent p-0 text-[.85rem] text-primary underline dark:text-primary-dark"
                                    data-att-url="/attachments/${a.getId().toString()}/view"
                                    data-att-kind="${ct.equals("application/pdf") ? "pdf" : "image"}"
                                    data-att-name="${a.getOriginalName()}">${msg.localize("att.view")}</button>
                        @endif
                    </td>
                    <td class="text-right whitespace-nowrap">${Fmt.fileSize(a.getSizeBytes())}</td>
                    <td class="whitespace-nowrap">${Fmt.dt(a.getCreatedAt(), zoneId)}</td>
                    <td>${uploaderNames.getOrDefault(a.getCreatedBy(), "-")}</td>
                    @if(canEdit != null && canEdit)
                        <td>
                            <form method="post" action="/attachments/${a.getId().toString()}/delete"
                                  hx-post="/attachments/${a.getId().toString()}/delete"
                                  hx-target="#attachments-${documentId.toString()}" hx-swap="outerHTML"
                                  hx-confirm="${msg.lookup("att.deleteConfirm")}"
                                  style="margin:0;">
                                @template.shared.csrf(csrf = csrf)
                                <button type="submit" class="cursor-pointer whitespace-nowrap rounded-radius border border-danger bg-danger px-2.5 py-1 text-center text-xs font-medium tracking-wide text-on-danger transition hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger active:opacity-100 active:outline-offset-0 disabled:cursor-not-allowed disabled:opacity-75">${msg.localize("att.delete")}</button>
                            </form>
                        </td>
                    @endif
                </tr>
            @endfor
            </tbody>
        `)
    @endif

    <%-- DEC-094: жойида кўриш модали (расм <img> / PDF <iframe>).
         Бўлим фрагменти ичида - hx-swap outerHTML'да у ҳам янгиланади
         (бир саҳифада битта бўлим = битта модал). Ёпиқ ҳолатда зарарсиз.
         Alpine ЭМАС, соф vanilla: fragment алмашинишида init талаб
         қилмасин. Кўрсатиш/ёпиш JS орқали (document-level delegation). --%>
    <%-- Кўриниш Penguin утилиталарда (DEC-122, helpDialog нақши:
         fixed марказ, мобилда max-md: bottom-sheet). att-modal /
         att-modal-title / att-modal-img / att-modal-frame /
         att-modal-newwin - JS СЕЛЕКТОР маркерлари (қуйидаги скрипт),
         display toggle'лари JS механикаси - inline қолади. --%>
    <div id="att-viewer" class="att-modal fixed inset-0 z-[142]" style="display:none;">
        <div class="fixed inset-0 bg-surface-dark/55" data-att-close></div>
        <div class="fixed left-1/2 top-1/2 flex max-h-[90vh] w-[min(900px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 flex-col rounded-radius bg-surface px-4 pb-4 pt-3.5 text-on-surface shadow-xl max-md:bottom-0 max-md:left-0 max-md:right-0 max-md:top-auto max-md:max-h-[92vh] max-md:w-full max-md:translate-x-0 max-md:translate-y-0 max-md:rounded-b-none max-md:rounded-t-2xl dark:bg-surface-dark dark:text-on-surface-dark" role="dialog" aria-modal="true" aria-label="${msg.lookup("att.view")}">
            <div class="mb-2.5 flex items-center justify-between gap-3">
                <b class="att-modal-title overflow-hidden text-ellipsis whitespace-nowrap text-[.95rem] text-on-surface-strong dark:text-on-surface-dark-strong"></b>
                <span class="flex shrink-0 items-center gap-2.5">
                    <a class="att-modal-newwin text-[.85rem] text-primary underline dark:text-primary-dark" href="#" target="_blank" rel="noopener">${msg.localize("att.viewNewWindow")}</a>
                    <button type="button" class="cursor-pointer rounded-radius border-0 bg-transparent px-1.5 text-2xl leading-none text-current/60 transition hover:bg-surface-alt hover:text-on-surface-strong dark:hover:bg-surface-dark-alt dark:hover:text-on-surface-dark-strong" data-att-close
                            aria-label="${msg.lookup("common.close")}">&times;</button>
                </span>
            </div>
            <div class="flex min-h-0 flex-1 items-start justify-center overflow-auto">
                <%-- Катта расм модалдан ошиб кетмасин (max чегаралар) --%>
                <img class="att-modal-img max-h-[80vh] max-w-full object-contain max-md:max-h-[70vh]" alt="" style="display:none;">
                <iframe class="att-modal-frame h-[80vh] w-full border-0 max-md:h-[70vh]" title="${msg.lookup("att.view")}" style="display:none;"></iframe>
            </div>
        </div>
    </div>
</section>
<script>
    // DEC-094: иловаларни жойида кўриш + clipboard paste.
    // Document-level delegation - фрагмент hx-swap outerHTML'да алмашса
    // ҳам тингловчилар document'да қолади (helpbtn/helpDialog претседенти).
    // Гвард: фрагмент ҳар swap'да бу <script>'ни қайта бажаради - шунда
    // тингловчилар ДУБЛЯЖ бўлмасин учун бир мартагина уланади.
    if (!window.__attInit) {
        window.__attInit = true;

        // Ёрдамчи: очиқ модални (ёки барчасини) ёпади ва src тозалайди
        // (iframe/img фонда юкланиб турмасин).
        var closeAttViewer = function () {
            document.querySelectorAll('.att-modal').forEach(function (m) {
                m.style.display = 'none';
                var img = m.querySelector('.att-modal-img');
                var frame = m.querySelector('.att-modal-frame');
                if (img) { img.style.display = 'none'; img.removeAttribute('src'); }
                if (frame) { frame.style.display = 'none'; frame.removeAttribute('src'); }
            });
        };

        // «Кўриш» тугмаси ёки ёпиш элементлари - битта click delegation.
        document.addEventListener('click', function (e) {
            if (!e.target || !e.target.closest) return;
            if (e.target.closest('[data-att-close]')) { closeAttViewer(); return; }
            var btn = e.target.closest('.att-view-btn');
            if (!btn) return;
            var url = btn.getAttribute('data-att-url');
            var kind = btn.getAttribute('data-att-kind');
            var name = btn.getAttribute('data-att-name') || '';
            var modal = document.getElementById('att-viewer');
            if (!modal || !url) return;
            var img = modal.querySelector('.att-modal-img');
            var frame = modal.querySelector('.att-modal-frame');
            var title = modal.querySelector('.att-modal-title');
            var newwin = modal.querySelector('.att-modal-newwin');
            if (title) title.textContent = name;
            if (newwin) newwin.setAttribute('href', url);
            // PDF - iframe, расм - img. Иккинчисини яшириб биттасини кўрсатамиз.
            if (kind === 'pdf') {
                if (img) { img.style.display = 'none'; img.removeAttribute('src'); }
                if (frame) { frame.setAttribute('src', url); frame.style.display = 'block'; }
            } else {
                if (frame) { frame.style.display = 'none'; frame.removeAttribute('src'); }
                if (img) { img.setAttribute('src', url); img.style.display = 'block'; }
            }
            modal.style.display = 'block';
        });

        // Esc - модални ёпади.
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') closeAttViewer();
        });

        // Clipboard paste: расм бўлса мавжуд юклаш формасига (file input)
        // қўйиб htmx оқими билан юборамиз - CSRF/target/swap формада бор,
        // такрор мантиқ ёзилмайди. Фақат upload формаси бор саҳифада
        // (canEdit) ишлайди; сервер барибир ҳақиқат манбаи (VIEWER 403).
        document.addEventListener('paste', function (e) {
            var cd = e.clipboardData;
            if (!cd || !cd.items) return;
            var section = document.querySelector('section[id^="attachments-"]');
            if (!section) return;
            var form = section.querySelector('form[enctype="multipart/form-data"]');
            var fileInput = form ? form.querySelector('input[type="file"]') : null;
            if (!form || !fileInput) return; // upload формаси йўқ (VIEWER) - жим ўтади
            var blob = null;
            for (var i = 0; i < cd.items.length; i++) {
                if (cd.items[i].type && cd.items[i].type.indexOf('image/') === 0) {
                    blob = cd.items[i].getAsFile();
                    break;
                }
            }
            if (!blob) return; // расм эмас (оддий матн paste) - аралашмаймиз
            e.preventDefault();
            // Ном: paste-YYYYMMDD-HHmmss.<ext> (ext blob туридан).
            var ext = (blob.type.split('/')[1] || 'png').toLowerCase();
            if (ext === 'jpeg') ext = 'jpg';
            var d = new Date();
            var p = function (n) { return (n < 10 ? '0' : '') + n; };
            var stamp = '' + d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate())
                    + '-' + p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds());
            var file = new File([blob], 'paste-' + stamp + '.' + ext,
                    { type: blob.type });
            // file input'га программ равишда File қўйиш - DataTransfer орқали.
            var dt = new DataTransfer();
            dt.items.add(file);
            fileInput.files = dt.files;
            // htmx мавжуд hx-post оқимини ишга солади (submit event).
            if (window.htmx && typeof window.htmx.trigger === 'function') {
                window.htmx.trigger(form, 'submit');
            } else {
                form.requestSubmit();
            }
        });
    }
</script>
