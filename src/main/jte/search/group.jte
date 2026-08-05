@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.search.service.SearchHit
@import java.util.List
@param Msg msg
@param String title
@param List<SearchHit> hits

<%-- Битта қидирув гуруҳи (ҳужжат/контакт/товар/счёт/экран) - палитра
     modal'ида ва /search саҳифасида бир хил ишлатилади. Бўш гуруҳ умуман
     чиқмайди. Кўриниш Penguin утилиталарда (DEC-122): гуруҳлараро
     чизиқ [&+&] variant'ида (класс сатри бир хил бўлгани учун ишлайди).
     search-hit / search-hit-label - JS МАРКЕР класслари (main.jte palette
     hits()/highlight() селекторлари, ↑/↓/Enter навигацияси), услуб бермайди;
     .on ҳолатини ҳам JS қўяди - кўриниши [&.on] variant'ида. --%>
@if(!hits.isEmpty())
    <div class="py-0.5 [&+&]:mt-0.5 [&+&]:border-t [&+&]:border-outline dark:[&+&]:border-outline-dark">
        <div class="px-2.5 pb-0.5 pt-1.5 text-[.7rem] uppercase tracking-[.08em] text-current/60">${title}</div>
        @for(SearchHit hit : hits)
            <a class="search-hit block rounded-radius px-2.5 py-2 text-on-surface no-underline transition hover:bg-primary/5 [&.on]:bg-primary/5 dark:text-on-surface-dark dark:hover:bg-primary-dark/10 dark:[&.on]:bg-primary-dark/10" href="${hit.url()}">
                <span class="search-hit-label block overflow-hidden text-ellipsis whitespace-nowrap text-[.92rem]">${hit.label()}</span>
                @if(hit.sublabel() != null)
                    <span class="mt-0.5 block overflow-hidden text-ellipsis whitespace-nowrap text-[.8rem] text-current/60">${hit.sublabel()}</span>
                @endif
            </a>
        @endfor
    </div>
@endif
