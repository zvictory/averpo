@param String key
@param String label
@param String baseUrl
@param String sortKey = null
@param String sortDir = null
@param String filterQuery = ""
@param String cls = null

<%-- Сараланадиган устун сарлавҳаси (DEC-105б, BA-007 P1b).
     Босилса шу устун бўйича asc, қайта босилса тескари; линк жорий
     филтрларни (filterQuery - sort'сиз!) сақлайди ва 1-саҳифага
     қайтаради (тартиб ўзгарганда кесим силжимасин); саҳифа ҳажми
     cookie'да (PageSizeResolver) - линкка керак эмас. Стрелка жорий
     ҳолатни кўрсатади: ↑ asc, ↓ desc. Penguin table канони: янги CSS
     класс йўқ, ранг thead'дан мерос (text-inherit), hover primary;
     статик «&amp;» - HTML-валидлик (pagination.jte билан бир хил). --%>
!{boolean active = key.equals(sortKey);}
!{String nextDir = active && "asc".equals(sortDir) ? "desc" : "asc";}
<th class="${cls}"><a class="inline-flex items-center gap-1 text-inherit no-underline transition hover:text-primary dark:hover:text-primary-dark" href="${baseUrl}?sort=${key}&amp;dir=${nextDir}${filterQuery}">${label}@if(active)<span aria-hidden="true">${"asc".equals(sortDir) ? "↑" : "↓"}</span>@endif</a></th>
