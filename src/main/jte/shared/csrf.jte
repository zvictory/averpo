@param org.springframework.security.web.csrf.CsrfToken csrf

<%-- DEC-098 (UI-LIB): ҳар POST форма бошидаги CSRF hidden input -
     аввал 114 жойда қўлда такрорланган учлик. null текшируви шарти
     сақланган: тестда/анонимда token бўлмаса форма input'сиз чиқади,
     хулқ мавжуд inline вариант билан айнан бир хил. --%>
@if(csrf != null)<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">@endif
