@import com.averpo.erp.i18n.Msg
@param org.springframework.data.domain.Page<?> page
@param String baseUrl
@param Msg msg
@param String filterQuery = ""

<%-- Умумий саҳифалаш (PERF-perf1 + DEC-105 QBO мукаммал pager;
     Penguin кўриниши - DEC-120, реф: docs/penguin-refs/
     pagination.html): ҳажм танлагичи (25/50/100/200) + Биринчи/
     Олдинги/Кейинги/Охирги + «жами X та, Y-Z кўрсатиляпти». Ҳажм
     cookie'да эсланади (сервер - PageSizeResolver); бу ерда фақат
     кўрсатиш. filterQuery жорий филтрларни («&status=...») ҳар линкда
     сақлайди; size'ни ҳам page линклари сақлайди (page.getSize()'дан -
     қўшимча параметр йўқ). Ҳажм ўзгарса ?size= билан 1-саҳифага
     қайтади (кесим силжимасин). Бир саҳифагина бўлса ҳам ҳисоб сатри
     ва танлагич кўринади; бутунлай бўш рўйхатда ҳеч нарса чиқмайди
     (экраннинг ўз хабари бор). Тугмалар Penguin ghost линк услубида,
     ҳисоб сатри text-on-surface (хира эмас - фойдаланувчи талаби).
     dark: жуфтлари бор (DEC-122): боди Penguin токенларида -
     тўқ режимда бутун саҳифа тўқ, пагинация ҳам жуфтлар билан
     ўқилади; Аралашда контент оч, жуфтлар ёнмайди. --%>
@if(page != null && page.getTotalElements() > 0)
    !{int cur = page.getNumber();}
    !{int last = java.lang.Math.max(0, page.getTotalPages() - 1);}
    !{int sz = page.getSize();}
    <nav aria-label="pagination" class="mt-4 flex flex-wrap items-center gap-2 text-sm">
        <%-- Ҳажм танлагичи: танлаганда ?size= билан 1-саҳифага юкланади
             (page ЙЎҚ). onchange - минимал JS (QBO услуби select). --%>
        <label class="flex items-center gap-1.5 text-on-surface dark:text-on-surface-dark">
            ${msg.localize("page.size")}
            <select onchange="location.href=this.value"
                    class="w-fit cursor-pointer rounded-radius border border-outline bg-surface px-2 py-1.5 text-sm text-on-surface transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:border-outline-dark dark:bg-surface-dark dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
                @for(var opt : java.util.List.of(25, 50, 100, 200))
                    <option value="${baseUrl}?size=${opt}${filterQuery}" selected="${sz == opt}">${opt}</option>
                @endfor
            </select>
        </label>

        <%-- Биринчи + Олдинги (фақат олдинги саҳифа бор бўлса).
             href'даги статик «&amp;» - HTML-валидлик (хом «&» атрибутда
             ambiguous ampersand); браузер иккисини
             ҳам бир хил ўқийди, validator энди шикоят қилмайди. --%>
        @if(page.hasPrevious())
            <a class="flex items-center gap-1 rounded-radius p-1 font-medium text-on-surface no-underline transition hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:text-primary-dark dark:focus-visible:outline-primary-dark" href="${baseUrl}?page=0&amp;size=${sz}${filterQuery}"
               title="${msg.lookup("page.first")}">«</a>
            <a class="flex items-center gap-1 rounded-radius p-1 font-medium text-on-surface no-underline transition hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:text-primary-dark dark:focus-visible:outline-primary-dark" href="${baseUrl}?page=${cur - 1}&amp;size=${sz}${filterQuery}">← ${msg.localize("page.prev")}</a>
        @endif

        <span class="text-on-surface dark:text-on-surface-dark">
            ${msg.localize("page.range",
                    String.valueOf(page.getTotalElements()),
                    String.valueOf(cur * sz + 1),
                    String.valueOf(cur * sz + page.getNumberOfElements()))}
        </span>

        <%-- Кейинги + Охирги (фақат кейинги саҳифа бор бўлса) --%>
        @if(page.hasNext())
            <a class="flex items-center gap-1 rounded-radius p-1 font-medium text-on-surface no-underline transition hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:text-primary-dark dark:focus-visible:outline-primary-dark" href="${baseUrl}?page=${cur + 1}&amp;size=${sz}${filterQuery}">${msg.localize("page.next")} →</a>
            <a class="flex items-center gap-1 rounded-radius p-1 font-medium text-on-surface no-underline transition hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-on-surface-dark dark:hover:text-primary-dark dark:focus-visible:outline-primary-dark" href="${baseUrl}?page=${last}&amp;size=${sz}${filterQuery}"
               title="${msg.lookup("page.last")}">»</a>
        @endif
    </nav>
@endif
