@import com.averpo.erp.shared.web.Fmt
@import java.math.BigDecimal
@import java.util.List
@param List<BigDecimal> cells
@param BigDecimal total
@param String title
@param boolean emphasize = false

<%-- Ҳисобланган қатор (Ялпи фойда, Операцион фойда, Соф фойда...) -
     устун кесимида ҳам, жамида ҳам. --%>
<tr class="${emphasize ? "border-t-2 border-outline bg-success/10 dark:border-outline-dark" : "border-t border-outline dark:border-outline-dark"}">
    <td class="font-bold text-on-surface-strong dark:text-on-surface-dark-strong">${title}</td>
    @for(BigDecimal cell : cells)
        <td class="text-right font-semibold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(cell)}</td>
    @endfor
    <td class="text-right font-bold whitespace-nowrap tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong">${Fmt.money(total)}</td>
</tr>
