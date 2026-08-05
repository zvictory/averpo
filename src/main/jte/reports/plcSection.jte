@import com.averpo.erp.ledger.service.ProfitAndLossByClassService.Row
@import com.averpo.erp.ledger.service.ProfitAndLossByClassService.Section
@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@param Section section
@param String title
@param String totalTitle

<%-- P&L by Class бўлими: сарлавҳа + счёт сатрлари (устун кесимида) +
     бўлим жамиси. Бўш бўлим ҳам сарлавҳа/жами билан чиқади - P&L
     тузилмаси доим тўлиқ кўринади (QBO услуби). --%>
<tr class="bg-surface-alt dark:bg-surface-dark-alt">
    <td colspan="${section.totals().size() + 2}" class="font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${title}</td>
</tr>
@for(Row row : section.rows())
    <tr>
        <td style="padding-left:1.25rem;">${row.code() != null ? row.code() + " " : ""}${row.name()}</td>
        @for(BigDecimal cell : row.cells())
            <td class="text-right whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(cell)}</td>
        @endfor
        <td class="text-right font-semibold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(row.total())}</td>
    </tr>
@endfor
<tr class="border-t border-outline dark:border-outline-dark">
    <td class="font-semibold text-on-surface-strong dark:text-on-surface-dark-strong">${totalTitle}</td>
    @for(BigDecimal total : section.totals())
        <td class="text-right font-semibold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(total)}</td>
    @endfor
    <td class="text-right font-bold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(section.total())}</td>
</tr>
