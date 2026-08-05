@import com.averpo.erp.i18n.Msg
@import com.averpo.erp.inventory.domain.Warehouse
@import com.averpo.erp.item.domain.Item
@import com.averpo.erp.item.domain.Unit
@import com.averpo.erp.ledger.domain.Account
@import com.averpo.erp.purchase.web.BillForm
@import com.averpo.erp.shared.service.TxnClassService.ClassOption
@import com.averpo.erp.tax.domain.TaxRate
@import java.util.List
@param int index
@param List<Item> items
@param List<Unit> units
@param List<Warehouse> warehouses
@param List<Account> expenseAccounts
@param List<TaxRate> taxRates
@param Msg msg
@param BillForm.LineForm line = null
@param String classMode = "OFF"
@param List<ClassOption> classes = java.util.List.of()
@param Boolean canEdit = false

<%-- Сатр тури Alpine билан бошқарилади: ITEM'да товар/омбор/миқдор/нарх,
     EXPENSE'да счёт+сумма, LANDED_COST'да фақат сумма кўринади. Керакмас
     майдонлар фақат яширилади - қийматини сервер тури бўйича ташлайди. --%>
<tr class="line" data-index="${index}"
    x-data="{t:'${line != null && line.getType() != null ? line.getType() : "ITEM"}'}">
    <td class="text-center text-current/60">${index + 1}</td>
    <td>
        <select name="lines[${index}].type" x-model="t" class="rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark">
            <option value="ITEM" selected="${line == null || "ITEM".equals(line.getType())}">${msg.localize("bill.type.ITEM")}</option>
            <option value="EXPENSE" selected="${line != null && "EXPENSE".equals(line.getType())}">${msg.localize("bill.type.EXPENSE")}</option>
            <option value="LANDED_COST" selected="${line != null && "LANDED_COST".equals(line.getType())}">${msg.localize("bill.type.LANDED_COST")}</option>
        </select>
    </td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].itemId", msg = msg,
                value = line != null ? line.getItemId() : null,
                placeholder = msg.lookup("bill.row.selectItem"),
                dense = true,
                cls = "w-full min-w-[160px]",
                xShow = "t === 'ITEM'",
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("bill.row.selectItem"))
                    @for(Item item : items)
                        @template.shared.comboOption(value = item.getId().toString(),
                                label = item.getName(),
                                selected = line != null && item.getId().toString().equals(line.getItemId()))
                    @endfor
                `)
        <%-- DEC-014: QBO tree (EXPENSE кесими) --%>
        @template.shared.combobox(name = "lines[" + index + "].accountId", msg = msg,
                value = line != null ? line.getAccountId() : null,
                placeholder = msg.lookup("bill.row.selectAccount"),
                dense = true,
                cls = "w-full min-w-[160px]",
                xShow = "t === 'EXPENSE'",
                cloak = true,
                addUrl = canEdit != null && canEdit ? "/accounts/quick" : null,
                addLabel = canEdit != null && canEdit ? msg.lookup("combo.addAccount") : null,
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("bill.row.selectAccount"))
                    @template.shared.comboAccountOptions(accounts = expenseAccounts, msg = msg,
                            selected = line != null ? line.getAccountId() : null)
                `)
        <span x-show="t === 'LANDED_COST'" x-cloak class="text-sm text-current/60">
            ${msg.localize("bill.row.landedCostNote")}
        </span>
    </td>
    <td>
        @template.shared.combobox(name = "lines[" + index + "].warehouseId", msg = msg,
                value = line != null ? line.getWarehouseId() : null,
                placeholder = msg.lookup("bill.row.selectWarehouse"),
                dense = true,
                cls = "min-w-[140px]",
                xShow = "t === 'ITEM'",
                addUrl = canEdit != null && canEdit ? "/warehouses/quick" : null,
                addLabel = canEdit != null && canEdit ? msg.lookup("combo.addWarehouse") : null,
                options = @`
                    @template.shared.comboOption(value = "", label = msg.lookup("bill.row.selectWarehouse"))
                    @for(Warehouse warehouse : warehouses)
                        @template.shared.comboOption(value = warehouse.getId().toString(),
                                label = warehouse.getName(),
                                selected = line != null && warehouse.getId().toString().equals(line.getWarehouseId()))
                    @endfor
                `)
    </td>
    <td><input name="lines[${index}].quantity" size="7" inputmode="decimal" x-show="t === 'ITEM'"
               value="${line != null && line.getQuantity() != null ? line.getQuantity() : ""}"
               class="text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"></td>
    <td>
        <%-- UoM: item танлангач JS фақат ўша гуруҳ бирликларини қолдиради
             (billForm скрипти); options'да data-group/data-factor --%>
        <select name="lines[${index}].unitId" data-unitsel x-show="t === 'ITEM'" class="min-w-[80px] rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark"
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
    <td><input name="lines[${index}].unitPrice" size="11" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" x-show="t === 'ITEM'"
               value="${line != null && line.getUnitPrice() != null ? line.getUnitPrice() : ""}"
              ></td>
    <td><input name="lines[${index}].amount" size="11" class="money text-right rounded-radius border border-outline bg-surface-alt px-2 py-1.5 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-75 dark:border-outline-dark dark:bg-surface-dark-alt/50 dark:text-on-surface-dark dark:focus-visible:outline-primary-dark" inputmode="decimal" x-show="t !== 'ITEM'" x-cloak
               value="${line != null && line.getAmount() != null ? line.getAmount() : ""}"
              >
        <%-- BA-004: ITEM сатрида миқдор × нарх жонли кўрсатилади
             (billForm скрипти тўлдиради); server ҳисоби ўзгармайди --%>
        <span data-lineamount x-show="t === 'ITEM'" title="${msg.lookup("bill.row.autoAmount")}"
              class="block whitespace-nowrap text-right tabular-nums text-on-surface-strong dark:text-on-surface-dark-strong"></span>
    </td>
    <td>
        <%-- ҚҚС ставкаси (docs/modules/tax.md): бўш - солиқсиз; item
             default'дан prefill (billForm скрипти) --%>
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
