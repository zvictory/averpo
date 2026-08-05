@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.sales.web.InvoiceForm
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param int index
@param List<Item> items
@param List<Unit> units
@param List<Warehouse> warehouses
@param List<TaxRate> taxRates
@param Msg msg
@param InvoiceForm.LineForm line = null
@param String classMode = "OFF"
@param List<ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

<%-- Сатр тури item'нинг ItemType'идан келади: option'даги data-type'ни
     Alpine ўқийди - INVENTORY'да омбор кўринади, бошқасида яширин.
     Нарх prefill энди UoM скриптида (invoiceForm) - каталог нарх ×
     танланган бирлик factor'и. --%>
<tr class="line" data-index="${index}" x-data="{t:''}"
    x-init="t = $el.querySelector('[data-itemsel]').dataset.type || ''">
    <td class="text-center text-current/60">${index + 1}</td>
    <td>
        <%-- Товар combobox: тур (data-type) value input'да - қатор t
             ҳолати ва UoM скрипти ўша ердан ўқийди --%>
        !{var selItem = line != null ? items.stream().filter(it -> it.getId().toString().equals(line.getItemId())).findFirst().orElse(null) : null;}
        @template.shared.combobox(name = "lines[" + index + "].itemId", msg = msg,
                value = line != null ? line.getItemId() : null,
                placeholder = msg.lookup("sinv.row.selectItem"),
                dense = true,
                cls = "w-full min-w-[180px]",
                itemsel = true,
                dataType = selItem != null ? selItem.getType().name() : null,
                onchange = "t = $event.target.dataset.type || ''",
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("sinv.row.selectItem"))
                    @for(Item item : items)
                        @template.shared.comboOption(value = item.getId().toString(),
                                label = item.getName(),
                                selected = line != null && item.getId().toString().equals(line.getItemId()),
                                dataType = item.getType().name())
                    @endfor
                `)
    </td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].warehouseId", msg = msg,
                value = line != null ? line.getWarehouseId() : null,
                placeholder = msg.lookup("sinv.row.selectWarehouse"),
                dense = true,
                cls = "min-w-[140px]",
                xShow = "t === 'INVENTORY'",
                cloak = true,
                addUrl = canEdit != null && canEdit ? "/warehouses/quick" : null,
                addLabel = canEdit != null && canEdit ? msg.lookup("combo.addWarehouse") : null,
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("sinv.row.selectWarehouse"))
                    @for(Warehouse warehouse : warehouses)
                        @template.shared.comboOption(value = warehouse.getId().toString(),
                                label = warehouse.getName(),
                                selected = line != null && warehouse.getId().toString().equals(line.getWarehouseId()))
                    @endfor
                `)
        <span x-show="t !== '' && t !== 'INVENTORY'" x-cloak
              class="text-sm text-current/60">${msg.localize("sinv.row.noWarehouse")}</span>
    </td>
    <td><input name="lines[${index}].quantity" size="7" inputmode="decimal"
               value="${line != null && line.getQuantity() != null ? line.getQuantity() : ""}"
               class="text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td>
        <%-- UoM: item танлангач JS фақат ўша гуруҳ бирликларини қолдиради
             (invoiceForm скрипти); options'да data-group/data-factor --%>
        <%-- Бирлик - native select (сурув: UoM филтр option'ларни программ
             бошқаради, гуруҳда 1-5 ёзув) --%>
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
        <%-- ҚҚС ставкаси (docs/modules/tax.md): бўш - солиқсиз; item
             default'дан prefill (invoiceForm скрипти) --%>
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
    @if("PER_LINE".equals(classMode))
        <%-- Сатр даражасидаги Йўналиш (class-tracking.md PER_LINE) --%>
        <td>
            @template.shared.classSelect(classes = classes, msg = msg,
                    name = "lines[" + index + "].classId",
                    selected = line != null ? line.getClassId() : null)
        </td>
    @endif
    <td><input name="lines[${index}].memo"
               value="${line != null && line.getMemo() != null ? line.getMemo() : ""}"
               class="w-full min-w-[110px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td class="text-center">
        <button type="button" onclick="this.closest('tr').remove()"
                class="cursor-pointer rounded-radius px-2 py-0.5 text-base leading-none text-on-surface/60 transition hover:bg-danger/10 hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger dark:text-on-surface-dark/60"
                aria-label="${msg.lookup("common.deleteLine")}">✕</button>
    </td>
</tr>
