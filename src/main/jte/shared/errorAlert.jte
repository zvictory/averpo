@param String errorMessage

<%-- Arbitr-127: HTMX partial сўровида (hx-get/hx-post) server хатоси
     учун ихчам жавоб - тўлиқ error саҳифа фрагмент ичига swap бўлиб
     хунук чиқмаслиги учун. Кўриниш - shared/alert канони (danger).
     data-hx-error белгиси money-input.js даги htmx:beforeSwap
     тингловчисига керак: аввалги хато alert'ини топиб ўчиради (қайта
     уринишларда тўпланиб қолмасин). Бу view'ни фақат
     GlobalExceptionHandler/ErrorController HX-Request бранчи қайтаради
     (X-Averpo-Error header'и билан жуфт ҳолда). --%>
<div data-hx-error>
    @template.shared.alert(error = errorMessage)
</div>
