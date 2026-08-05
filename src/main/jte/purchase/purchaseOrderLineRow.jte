@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.purchase.web.PurchaseOrderForm
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param int index
@param List<Item> items
@param List<Unit> units
@param List<TaxRate> taxRates
@param Msg msg
@param PurchaseOrderForm.LineForm line = null

<%-- PO сатри - estimateLineRow'нинг харид томонидаги кўзгуси: омбор
     устуни йўқ (айлантиришда bill формасида танланади). --%>
<tr class="line" data-index="${index}">
    <td class="text-center text-current/60">${index + 1}</td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].itemId", msg = msg,
                value = line != null ? line.getItemId() : null,
                placeholder = msg.lookup("po.row.selectItem"),
                dense = true,
                cls = "w-full min-w-[180px]",
                itemsel = true,
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("po.row.selectItem"))
                    @for(Item item : items)
                        @template.shared.comboOption(value = item.getId().toString(),
                                label = item.getName(),
                                selected = line != null && item.getId().toString().equals(line.getItemId()))
                    @endfor
                `)
    </td>
    <td><input name="lines[${index}].quantity" size="7" inputmode="decimal"
               value="${line != null && line.getQuantity() != null ? line.getQuantity() : ""}"
               class="text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td>
        <%-- UoM: item танлангач JS фақат ўша гуруҳ бирликларини қолдиради --%>
        <select name="lines[${index}].unitId" data-unitsel class="min-w-[80px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"
               >
            <option value="">-</option>
            @for(Unit unit : units)
                <option value="${unit.getId().toString()}"
                        data-group="${unit.getGroup() == null ? "" : unit.getGroup().getId().toString()}"
                        data-factor="${unit.getFactor().stripTrailingZeros().toPlainString()}"
                        selected="${line != null && unit.getId().toString().equals(line.getUnitId())}">
                    ${unit.getName()}
                </option>
            @endfor
        </select>
    </td>
    <td><input name="lines[${index}].unitPrice" size="11" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal"
               value="${line != null && line.getUnitPrice() != null ? line.getUnitPrice() : ""}"
              ></td>
    <%-- Сатр суммаси (миқдор × нарх) - жонли, recompute ёзади (QBO паритет) --%>
    <td class="text-right"><span data-line-amount style="white-space:nowrap;font-variant-numeric:tabular-nums;" class="text-sm text-on-surface dark:text-on-surface-dark">0.00</span></td>
    <td>
        <%-- ҚҚС ставкаси (tax.md): фақат жонли кўрсатиш - GL йўқ --%>
        <select name="lines[${index}].taxRateId" data-taxsel class="min-w-[90px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <option value="">${msg.localize("tax.none")}</option>
            @for(TaxRate rate : taxRates)
                <option value="${rate.getId().toString()}"
                        data-rate="${rate.getRate().stripTrailingZeros().toPlainString()}"
                        selected="${line != null && rate.getId().toString().equals(line.getTaxRateId())}">
                    ${rate.getName()}
                </option>
            @endfor
        </select>
    </td>
    <td><input name="lines[${index}].memo"
               value="${line != null && line.getMemo() != null ? line.getMemo() : ""}"
               class="w-full min-w-[110px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td class="text-center">
        <button type="button" onclick="this.closest('tr').remove()"
                class="cursor-pointer rounded-radius px-2 py-0.5 text-base leading-none text-on-surface/60 transition hover:bg-danger/10 hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger dark:text-on-surface-dark/60"
                aria-label="${msg.lookup("common.deleteLine")}">✕</button>
    </td>
</tr>
