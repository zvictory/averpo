@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.ledger.web.AccountOptionGroups
@import java.util.List
@param List<Account> accounts
@param Msg msg
@param String selected = null
@param Boolean disableNonPostable = true

<%-- shared/accountOptions'нинг combobox твини (DEC-123): ўша
     AccountOptionGroups қурувчиси, native optgroup ўрнига comboGroup
     сарлавҳа, option ўрнига comboOption li. postable=false ота счётлар
     кўрсатилмайди - эски combobox.js dropdown хулқи айнан (у disabled
     option'ларни ўтказиб юборарди); disableNonPostable=false бўлса
     (ота счёт танлаш) ҳаммаси чиқади. data-cur/data-undep - тўлов
     формалари валюта филтри JS'и учун (DEC-070): танловда value
     input'га кўчади, филтр li.hidden билан ишлайди. --%>
@for(AccountOptionGroups.Group group : AccountOptionGroups.build(accounts, disableNonPostable != null && disableNonPostable))
    @template.shared.comboGroup(label = msg.lookup(group.titleKey()))
    @for(AccountOptionGroups.Option option : group.options())
        @if(!option.disabled())
            @template.shared.comboOption(
                value = option.id(),
                label = option.label(),
                selected = option.id().equals(selected),
                dataCur = option.currency(),
                dataUndep = option.undeposited() ? "1" : null,
                dataAcctype = option.type())
        @endif
    @endfor
@endfor
