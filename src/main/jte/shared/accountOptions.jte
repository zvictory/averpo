@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.ledger.web.AccountOptionGroups
@import java.util.List
@param List<Account> accounts
@param Msg msg
@param String selected = null
@param Boolean disableNonPostable = true

<%-- DEC-014: счёт танлашнинг ЯГОНА қолипи (QBO tree-select, MVP -
     native optgroup). Чақирувчи ўз <select> ўрамини сақлайди (атрибут/
     Alpine эркинлиги учун), бўш "танланг" option ҳам чақирувчида
     қолади - бу partial фақат optgroup + option'ларни беради.
     Гуруҳ: счёт тури; жилд: ота-бола чуқурлиги (NBSP);
     матн: код ном (валюта); postable=false ота счётлар disabled
     (disableNonPostable=false бўлса - масалан ота счёт танлашда -
     ҳаммаси танланади). Typeahead - кейинги босқич (Alpine).
     Истисно (QA-007): bank transfer'нинг иккита банк select'и
     АТАЙЛАБ бу partial'дан ташқарида - уларга option'даги
     data-currency/data-balance атрибутлари керак (Alpine боғланиши).
     data-cur/data-undep (DEC-070): тўлов формаларининг валюта филтри
     JS'и учун - data-cur йўқлиги home дегани, data-undep=1 клиринг
     чўнтаги (филтр яширмайди). --%>
@for(AccountOptionGroups.Group group : AccountOptionGroups.build(accounts, disableNonPostable != null && disableNonPostable))
    <optgroup label="${msg.localize(group.titleKey())}">
        @for(AccountOptionGroups.Option option : group.options())
            <option value="${option.id()}"
                    data-cur="${option.currency()}"
                    data-undep="${option.undeposited() ? "1" : null}"
                    disabled="${option.disabled()}"
                    selected="${option.id().equals(selected)}">${option.label()}</option>
        @endfor
    </optgroup>
@endfor
